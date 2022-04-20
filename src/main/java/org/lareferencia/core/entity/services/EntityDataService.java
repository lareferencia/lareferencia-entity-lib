/*
 *   Copyright (c) 2013-2022. LA Referencia / Red CLARA and others
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   This file is part of LA Referencia software platform LRHarvester v4.x
 *   For any further information please contact Lautaro Matas <lmatas@gmail.com>
 */

package org.lareferencia.core.entity.services;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityRelationType;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrenceContainer;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.Provenance;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.domain.SourceEntity;
import org.lareferencia.core.entity.domain.SourceRelation;
import org.lareferencia.core.entity.repositories.jpa.*;
import org.lareferencia.core.entity.xml.XMLEntityInstance;
import org.lareferencia.core.entity.xml.XMLEntityRelationData;
import org.lareferencia.core.entity.xml.XMLFieldValueInstance;
import org.lareferencia.core.entity.xml.XMLRelationInstance;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.util.date.DateHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import lombok.Getter;
import lombok.Setter;

public class EntityDataService {

	private static Logger logger = LogManager.getLogger(EntityDataService.class);

	@Autowired
	EntityTypeRepository entityTypeRepository;

	@Autowired
	RelationTypeRepository relationTypeRepository;

	@Autowired
	SourceEntityRepository sourceEntityRepository;

	@Autowired
	EntityRepository entityRepository;

	@Autowired
	SourceRelationRepository sourceRelationRepository;


	@Autowired
	private DateHelper dateHelper;

	@Autowired
	private FieldOccurrenceRepository fieldOccurrenceRepository;

	@Autowired
	private ProvenanceRepository provenanceRepository;
	
	@Autowired
	private SemanticIdentifierRepository semanticIdentifierRepository;

	//@Setter
	//@Getter
	//private EntityLRUCache entityCache = null;

	@Getter
	@Setter
	private Profiler profiler = new Profiler(false, "");

	ConcurrentCachedNamedStore<Long, EntityType,EntityTypeRepository> entityTypeStore;
	ConcurrentCachedNamedStore<Long,RelationType,RelationTypeRepository> relationTypeStore;
	SemanticIdentifierCachedStore semanticIdentifierCachedStore;
	ProvenanceStore provenanceStore;
	FieldOcurrenceCachedStore fieldOcurrenceCachedStore;


	public EntityDataService() {

	}

	@PostConstruct
	private void postConstruct() {
		entityTypeStore = new ConcurrentCachedNamedStore<>(entityTypeRepository,100,true,0);
		relationTypeStore = new ConcurrentCachedNamedStore<>(relationTypeRepository,100,true,0);

		semanticIdentifierCachedStore = new SemanticIdentifierCachedStore(semanticIdentifierRepository,1000);
		provenanceStore = new ProvenanceStore(provenanceRepository);
		fieldOcurrenceCachedStore = new FieldOcurrenceCachedStore(fieldOccurrenceRepository,1000);
	}

	@PreDestroy
	public void preDestroy() {

		// flush stores on destroy
		semanticIdentifierCachedStore.flush();
		fieldOcurrenceCachedStore.flush();
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void parseAndPersistEntityRelationDataFromXMLDocument(Document document) throws EntityRelationException {
		XMLEntityRelationData erData = parseEntityRelationDataFromXmlDocument(document);
		profiler.messure("EntityXML Parse");

		persistEntityRelationData(erData);

	}

	/**
	 * Load EntityRelation Data instance from XML Document
	 * 
	 * @param document
	 * @return
	 * @throws EntityRelationException
	 */
	public XMLEntityRelationData parseEntityRelationDataFromXmlDocument(Document document)
			throws EntityRelationException {

		XMLEntityRelationData erData = new XMLEntityRelationData();

		try {
			JAXBContext context = JAXBContext.newInstance(erData.getClass());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

			Unmarshaller unmarshaller = context.createUnmarshaller();

			erData = (XMLEntityRelationData) unmarshaller.unmarshal(document);

		} catch (Exception e) {
			throw new EntityRelationException("Error loading Entity-Relation data from XML Document " + e.getMessage());
		}

		// check consistency
		erData.isConsistent();

		return erData;
	}

	/**
	 * Persist a XMLEntityRelation Data instance in DB Metamodel Objects
	 * 
	 * @param data
	 * @throws EntityRelationException
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void persistEntityRelationData(XMLEntityRelationData data) throws EntityRelationException {

		Map<String, SourceEntity> entitiesByRef = new HashMap<String, SourceEntity>();
	
		if (data.getLastUpdate() == null || data.getSource() == null || data.getRecord() == null) {
			System.out.println( data.getLastUpdate() + data.getSource() + data.getRecord() == null );
			throw new EntityRelationException("Null field in provenacefound in xml entity definition");
		}

		Provenance provenance = provenanceStore.loadOrCreate(data.getSource(), data.getRecord());

		LocalDateTime lastUpdate = dateHelper.parseDate(data.getLastUpdate());
		
		Boolean isNew = provenance.getLastUpdate() == null;
		Boolean isUpdate = provenance.getLastUpdate() != null && provenance.getLastUpdate().isBefore(lastUpdate);
		
		if ( !isNew && !isUpdate )
			return;

		if ( isUpdate ) {
			// logically delete existing source entities because it will be created again
			sourceEntityRepository.logicalDeleteByProvenanceId(provenance.getId());
		}
				
		// entities
		for (XMLEntityInstance xmlEntity : data.getEntities()) {

			EntityType entityType = getEntityTypeFromName(xmlEntity.getType());

			profiler.messure(xmlEntity.getType() + " Source Entity", true);

			SourceEntity sourceEntity = new SourceEntity(entityType, provenance);

			for (XMLFieldValueInstance field : xmlEntity.getFields())
				addFieldOccurrenceFromXMLFieldInstance(entityType, sourceEntity, field);

			for (String semanticId : xmlEntity.getSemanticIdentifiers())
				if (isMinimalViableSemanticIdentifier(semanticId))
					sourceEntity.addSemanticIdentifier( semanticIdentifierCachedStore.loadOrCreate(semanticId) );
	
			profiler.messure("Persist Source Entity");
			
			// find existing entity o create a new one
			Entity entity = findOrCreateFinalEntity(sourceEntity);
			
			// set that entity as final entity for this source entity
			sourceEntity.setFinalEntity(entity);
			//sourceEntityRepository.updateFinalEntityReference(sourceEntity.getId(), entity.getId());
			
			profiler.messure("Find or Create Final Entity");
			
			// save the source entity 
			sourceEntityRepository.saveAndFlush(sourceEntity); // save source entity
			
			// copy semantic ids from source to entity
			//sourceEntityRepository.copySemanticIdentifiersFromSourceEntityToEntity(sourceEntity.getId(), entity.getId());
						
			profiler.messure("Save source entity");

			
			entitiesByRef.put(xmlEntity.getRef(), sourceEntity);
		}

		// relations
		for (XMLRelationInstance xmlRelation : data.getRelations()) {

			// Relation Type
			RelationType relationType = getRelationTypeFromName(xmlRelation.getType());

			SourceRelation sourceRelation = createRelationFromXMLEntityInstance(entitiesByRef, relationType, xmlRelation);	
			
			// for each relation define or update the occurrences
			for (XMLFieldValueInstance field : xmlRelation.getFields())
				addFieldOccurrenceFromXMLFieldInstance(relationType, sourceRelation, field);
	
			sourceRelationRepository.save(sourceRelation);
			
			//Relation relation = new Relation(relationType, sourceRelation.getFromEntity().getFinalEntity(), sourceRelation.getToEntity().getFinalEntity());
			//relationRepository.saveAndFlush(relation);

			
			profiler.messure("SourceRelation Persistence :: " + xmlRelation.getType());

		}
		
		// finally update provenance lastUpdate
		provenanceStore.setLastUpdate(provenance,lastUpdate);



	}

	public LocalDateTime parseLastUpdateDate(String lastUpdateString) {
		if (lastUpdateString != null)
			return dateHelper.parseDate(lastUpdateString);// DateUtil.stringToDate();
		else
			return null;
	}

	/**
	 * 
	 * @param entitiesByRef
	 * @param xmlRelation
	 * @throws EntityRelationException
	 */
	private SourceRelation createRelationFromXMLEntityInstance(Map<String, SourceEntity> entitiesByRef, RelationType relationType, XMLRelationInstance xmlRelation) throws EntityRelationException {

		
		SourceEntity fromEntity = entitiesByRef.get( xmlRelation.getFromEntityRef() );
		if (fromEntity == null)
			throw new EntityRelationException("Relation contains references to a inexistent From Entity relation:" + xmlRelation.getType() + " " + xmlRelation.getFromEntityRef() );

		SourceEntity toEntity = entitiesByRef.get( xmlRelation.getToEntityRef() );
		if (toEntity == null)
			throw new EntityRelationException("Relation contains references to a inexistent To Entity relation:" + xmlRelation.getType() + " " + xmlRelation.getToEntityRef() );

		SourceRelation relation = new SourceRelation(relationType, fromEntity, toEntity);
//		relation.setFromFinalEntity(fromEntity.getFinalEntity());
//		relation.setToFinalEntity(toEntity.getFinalEntity());
//		
		return relation;
	}

	
	// TODO: Implement a filter for minimal viable sematic identifier
	private Boolean isMinimalViableSemanticIdentifier(String semanticIdentifier) {
		return semanticIdentifier != null && semanticIdentifier.length() > 4;
	}


	/**
	 * Adds field occurrence to a container (Relation or Entity)
	 * 
	 * @param container
	 * @param field
	 * @throws EntityRelationException
	 */
	private void addFieldOccurrenceFromXMLFieldInstance(EntityRelationType type, FieldOccurrenceContainer container,
														XMLFieldValueInstance field) throws EntityRelationException {

		if (field.getName() != null && !field.getName().trim().isEmpty() ) {
			String fieldName = field.getName();
			
			try {
				FieldType fieldType = type.getFieldByName(fieldName);
				container.addFieldOccurrence( fieldOcurrenceCachedStore.loadOrCreate(fieldType, field) );
			} catch (EntityRelationException e) {
				logger.info("Ignoring field: " + fieldName + " :: " +  e.getMessage() );
			}
		}
	}

	public EntityType getEntityTypeFromName(String name) throws EntityRelationException {
		try {
			return entityTypeStore.getByName(name);
		} catch (CacheException e) {
			logger.error("Type: " + name + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + name + " does not exists in metamodel");
		}
	}

	public Optional<Entity> getEntityById(UUID entityId) {

		return entityRepository.findById(entityId);
	}

	public RelationType getRelationTypeFromName(String name) throws EntityRelationException {

		try {
			return relationTypeStore.getByName(name);
		} catch (CacheException e) {
			logger.error("Type: " + name + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + name + " does not exists in metamodel");
		}
	}

	public EntityType getEntityTypeFromId(Long id) throws EntityRelationException {

		EntityType type = entityTypeStore.get(id);

		if (type != null) {
			return type;
		} else {
			logger.error("Type: " + id + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + id + " does not exists in metamodel");
		}
	}

	public RelationType getRelationTypeFromId(Long id) throws EntityRelationException {

		RelationType type = relationTypeStore.get(id);

		// if exists in map return cached value
		if (type != null)
			return type;
		else {
			logger.error("Type: " + id + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + id + " does not exists in metamodel");
		}
	}

	public EntityType getEntityTypeByEntityId(UUID entityId) throws EntityRelationException {

		Long entityTypeId = entityRepository.getEntityTypeIdByEntityId(entityId);

		if (entityTypeId == null)
			throw new EntityRelationException("GetEntityTypeByEntityId - Entity: " + entityId + " does not exists");

		return getEntityTypeFromId(entityTypeId);
	}


	/************************
	 * Deduplication
	 ****************************************************/

	@Transactional
	public void deleteEntitiesById(Collection<UUID> entityIds) {
		for (UUID entityId : entityIds)
			entityRepository.deleteById(entityId);
	}

	
	public Entity findOrCreateFinalEntity(SourceEntity sourceEntity) {
		
		
			Collection<SemanticIdentifier> semanticIdentifiers = sourceEntity.getSemanticIdentifiers();
			List<Long> semanticIds = semanticIdentifiers.stream().map(SemanticIdentifier::getId).collect(Collectors.toList());
	
			Entity entity = entityRepository.findEntityWithSemanticIdentifiers(semanticIds);
			
			if (entity == null ) { // No entities with shared semantic identifiers exists the create
				entity = new Entity(sourceEntity.getEntityType());
			} 
			
			entity.setDirty(true);
			entity.addSemanticIdentifiers(semanticIdentifiers);
			entityRepository.saveAndFlush(entity);
						
			return entity;
	}

	@Transactional 
	public synchronized void mergeEntityRelationData() {
		entityRepository.mergeEntiyRelationData();
	}
	
	public List<Entity> findEntitiesByProvenanceSourceAndRecordId(String sourceId, String recordId) {
		return entityRepository.findByProvenaceSourceAndRecordId(sourceId, recordId);
	}
	
	public Page<Entity> findEntitiesByProvenanceSource(String sourceId, Pageable pageable) {
		return entityRepository.findEntitiesByProvenaceSource(sourceId, pageable);
	}
	


}
