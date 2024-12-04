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

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityRelationType;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrenceContainer;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.Provenance;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.domain.SourceEntity;
import org.lareferencia.core.entity.domain.SourceRelation;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.FieldOccurrenceRepository;
import org.lareferencia.core.entity.repositories.jpa.ProvenanceRepository;
import org.lareferencia.core.entity.repositories.jpa.RelationTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.SemanticIdentifierRepository;
import org.lareferencia.core.entity.repositories.jpa.SourceEntityRepository;
import org.lareferencia.core.entity.repositories.jpa.SourceRelationRepository;
import org.lareferencia.core.entity.services.exception.EntitiyRelationXMLLoadingException;
import org.lareferencia.core.entity.xml.XMLEntityInstance;
import org.lareferencia.core.entity.xml.XMLEntityRelationData;
import org.lareferencia.core.entity.xml.XMLFieldValueInstance;
import org.lareferencia.core.entity.xml.XMLRelationInstance;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.util.date.DateHelper;
import org.springframework.beans.factory.annotation.Autowired;
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

	// @Setter
	// @Getter
	// private EntityLRUCache entityCache = null;

	@Getter
	@Setter
	private Profiler profiler = new Profiler(false, "");

	ConcurrentCachedNamedStore<Long, EntityType, EntityTypeRepository> entityTypeStore;
	ConcurrentCachedNamedStore<Long, RelationType, RelationTypeRepository> relationTypeStore;
	SemanticIdentifierCachedStore semanticIdentifierCachedStore;
	ProvenanceStore provenanceStore;
	FieldOcurrenceCachedStore fieldOcurrenceCachedStore;

	public EntityDataService() {

	}

	@PostConstruct
	private void postConstruct() {
		entityTypeStore = new ConcurrentCachedNamedStore<>(entityTypeRepository, 100, true, 0);
		relationTypeStore = new ConcurrentCachedNamedStore<>(relationTypeRepository, 100, true, 0);

		semanticIdentifierCachedStore = new SemanticIdentifierCachedStore(semanticIdentifierRepository, 1000);
		provenanceStore = new ProvenanceStore(provenanceRepository);
		fieldOcurrenceCachedStore = new FieldOcurrenceCachedStore(fieldOccurrenceRepository, 1000);
	}

	@PreDestroy
	public void preDestroy() {

		// flush stores on destroy
		semanticIdentifierCachedStore.flush();
		fieldOcurrenceCachedStore.flush();
	}

	
	/**
	 * Load EntityRelation Data instance from XML Document and persist it
	 * 
	 * @param document XML Document
	 * @param dryRun if true, no data will be persisted
	 * @throws Exception
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public EntityLoadingStats parseAndPersistEntityRelationDataFromXMLDocument(Document document, Boolean dryRun) throws Exception {
		XMLEntityRelationData erData = parseEntityRelationDataFromXmlDocument(document);
		profiler.messure("EntityXML Parse");
		return persistEntityRelationData(erData, dryRun);
	}

	/**
	 * Load EntityRelation Data instance from XML Document
	 * 
	 * @param document
	 * @return
	 * @throws EntitiyRelationXMLLoadingException
	 */
	public XMLEntityRelationData parseEntityRelationDataFromXmlDocument(Document document) throws Exception {

		XMLEntityRelationData erData = new XMLEntityRelationData();

		try {
			JAXBContext context = JAXBContext.newInstance(erData.getClass());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

			Unmarshaller unmarshaller = context.createUnmarshaller();

			erData = (XMLEntityRelationData) unmarshaller.unmarshal(document);

		} catch (Exception e) {
			throw new EntitiyRelationXMLLoadingException (
					"Error parsing XML File to Entity-Relation data :: " + e.getMessage());		
		}

		// check consistency
		try {
			erData.isConsistent();
		} catch (Exception imfex) {
			throw new EntitiyRelationXMLLoadingException( "Entity-Relation data is no consistent :: " + imfex.getMessage());
		}

		return erData;
	}

	/**
	 * Persist a XMLEntityRelation Data instance in DB Metamodel Objects
	 * 
	 * @param data
	 * @throws EntitiyRelationXMLLoadingException
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public EntityLoadingStats persistEntityRelationData(XMLEntityRelationData data, Boolean dryRun) throws Exception {

		EntityLoadingStats stats = new EntityLoadingStats();

		Map<String, SourceEntity> entitiesByRef = new HashMap<String, SourceEntity>();

		if (data.getLastUpdate() == null) {
			throw new EntitiyRelationXMLLoadingException(
					"The LastUpdate field is Null on provenace for the given XML entity definition :: " + data.getLastUpdate());
		}
		if (data.getSource() == null) {
			throw new EntitiyRelationXMLLoadingException(
					"The Source field is Null on provenace for the given XML entity definition :: " + data.getSource());
		}
		if (data.getRecord() == null) {
			throw new EntitiyRelationXMLLoadingException(
					"The Record field is Null on provenace for the given XML entity definition :: " + data.getRecord());
		}

		// provenance
		Provenance provenance = null;		
		if (dryRun) // if dry run, do not persist provenance 
			provenance = new Provenance(data.getSource(), data.getRecord());
		else // if not dry run, load or create provenance
			provenance = provenanceStore.loadOrCreate(data.getSource(), data.getRecord());


		// last update
		LocalDateTime lastUpdate = null;
		try {
			lastUpdate = dateHelper.parseDate(data.getLastUpdate());
			if (!dateHelper.isValidLocalDateTime(lastUpdate)) {
				throw new EntitiyRelationXMLLoadingException(
						"The LastUpdate field is not valid :: lastUpdate: " + lastUpdate);
			}
		} catch (Exception e) {
			throw new EntitiyRelationXMLLoadingException(
					"The LastUpdate field is not valid :: lastUpdate: " + lastUpdate);
		}


		// check if the provenance is new or update
		Boolean isNew = provenance.getLastUpdate() == null;
		Boolean isUpdate = provenance.getLastUpdate() != null && provenance.getLastUpdate().isBefore(lastUpdate);

		// if is not new and is not update, do nothing
		if (!isNew && !isUpdate) return stats;

		if (isUpdate) // logically delete existing source entities related with this provenance because they will be replaced
			sourceEntityRepository.logicalDeleteByProvenanceId(provenance.getId());
		
		// iterate over entities in the XML
		for (XMLEntityInstance xmlEntity : data.getEntities()) {

			// TODO: throw exception if the entity type is not defined in the model
			EntityType entityType = getEntityTypeFromName(xmlEntity.getType());

			profiler.messure(xmlEntity.getType() + " Source Entity", true);

			SourceEntity sourceEntity = new SourceEntity(entityType, provenance);

			for (XMLFieldValueInstance field : xmlEntity.getFields())
				addFieldOccurrenceFromXMLFieldInstance(entityType, sourceEntity, field);

			// add semantic identifiers 
			Boolean isAtLeastOneMinimalViableSemanticIdentifier = false;	
			for (String semanticId : xmlEntity.getSemanticIdentifiers())
				if (isMinimalViableSemanticIdentifier(semanticId)) {
					if (!dryRun) // if not dry run, load or create semantic identifier
						sourceEntity.addSemanticIdentifier(semanticIdentifierCachedStore.loadOrCreate(semanticId));
					isAtLeastOneMinimalViableSemanticIdentifier = true;
				}
			
			// if there is no semantic identifier, throw exception
			if (!isAtLeastOneMinimalViableSemanticIdentifier) {
				throw new EntitiyRelationXMLLoadingException("The provided XML Entity does not contain at least one semanticIdentifier ::  Entity: " + xmlEntity.getRef());
			}

			profiler.messure("Find or Create Final Entity");		
			// find existing entity o create a new one
			FindOrCreateEntityResult findOrCreateFinalEntityResult = findOrCreateFinalEntity(sourceEntity);

			// if the entity is new, increment entities stats, if not, increment duplications found stats
			if (!findOrCreateFinalEntityResult.entityAlreadyExists)
				stats.incrementEntitiesCreated(); // increment entities stats because the entity is new
			else	
				stats.incrementEntitiesDuplicated(); // increment duplications found stats because the entity already exists
		
			// set that entity as final entity for this source entity
			sourceEntity.setFinalEntity(findOrCreateFinalEntityResult.entity);

			// sourceEntityRepository.updateFinalEntityReference(sourceEntity.getId(),
			// entity.getId());

			// save the source entity
			profiler.messure("Persist Source Entity");
			if (!dryRun) // if not dry run, save source entity
				sourceEntityRepository.saveAndFlush(sourceEntity); // save source entity

			stats.incrementSourceEntitiesLoaded(); // increment stats


			// copy semantic ids from source to entity
			// sourceEntityRepository.copySemanticIdentifiersFromSourceEntityToEntity(sourceEntity.getId(),
			// entity.getId());

			profiler.messure("Save source entity");

			// add the source entity to the map for later use in relations
			entitiesByRef.put(xmlEntity.getRef(), sourceEntity);
		}

		// for each relation
		for (XMLRelationInstance xmlRelation : data.getRelations()) {

			// Relation Type
			// TODO: throw exception if the entity type is not defined in the model
			RelationType relationType = getRelationTypeFromName(xmlRelation.getType());

			SourceRelation sourceRelation = createRelationFromXMLEntityInstance(entitiesByRef, relationType,
					xmlRelation);

			// for each relation define or update the occurrences
			for (XMLFieldValueInstance field : xmlRelation.getFields())
				addFieldOccurrenceFromXMLFieldInstance(relationType, sourceRelation, field);

			if (!dryRun) // if not dry run, save source relation
				sourceRelationRepository.save(sourceRelation);

			stats.incrementSourceRelationsLoaded(); // increment stats

			profiler.messure("SourceRelation Persistence :: " + xmlRelation.getType());

		}

		// finally update provenance lastUpdate
		if (!dryRun) // if not dry run, update provenance last update
			provenanceStore.setLastUpdate(provenance, lastUpdate);


		//TODO: check if the model contains at least 1 source entity
		// if (sourceEntitiesCount >= 1 ) {
		// 	throw new InvalidEntityModelException(
		// 			"The provided XML Entity file does not contains relations to correlate the defined entities.");
		// }

		//TODO: report sourceentity count, entity count and source relation count to monitor

		return stats;

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
	 * @throws EntitiyRelationXMLLoadingException
	 */
	private SourceRelation createRelationFromXMLEntityInstance(Map<String, SourceEntity> entitiesByRef,
			RelationType relationType, XMLRelationInstance xmlRelation) throws EntitiyRelationXMLLoadingException {

		SourceEntity fromEntity = entitiesByRef.get(xmlRelation.getFromEntityRef());
		if (fromEntity == null)
			throw new EntitiyRelationXMLLoadingException("Relation contains references to a inexistent From Entity relation :: "
					+ xmlRelation.getType() + " " + xmlRelation.getFromEntityRef());

		SourceEntity toEntity = entitiesByRef.get(xmlRelation.getToEntityRef());
		if (toEntity == null)
			throw new EntitiyRelationXMLLoadingException("Relation contains references to a inexistent To Entity relation :: "
					+ xmlRelation.getType() + " " + xmlRelation.getToEntityRef());

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
	 * @throws EntitiyRelationXMLLoadingException
	 */
	private void addFieldOccurrenceFromXMLFieldInstance(EntityRelationType type, FieldOccurrenceContainer container,
			XMLFieldValueInstance field) throws EntitiyRelationXMLLoadingException {

		if (field.getName() != null && !field.getName().trim().isEmpty()) {
			String fieldName = field.getName();

			try {
				FieldType fieldType = type.getFieldByName(fieldName);
				container.addFieldOccurrence(fieldOcurrenceCachedStore.loadOrCreate(fieldType, field));
			} catch (EntityRelationException e) {
				throw new EntitiyRelationXMLLoadingException("Unknown fieldName found in data :: " + e.getMessage());
			}
		}
	}

	public EntityType getEntityTypeFromName(String name) throws EntitiyRelationXMLLoadingException {
		try {
			return entityTypeStore.getByName(name);
		} catch (CacheException e) {
			logger.error("Type: " + name + " does not exists in metamodel");
			throw new EntitiyRelationXMLLoadingException("Unknown EntityTypeName for this model db :: "  + name + " does not exists in metamodel");
		}
	}

	public Optional<Entity> getEntityById(UUID entityId) {

		return entityRepository.findById(entityId);
	}

	public RelationType getRelationTypeFromName(String name) throws EntitiyRelationXMLLoadingException {

		try {
			return relationTypeStore.getByName(name);
		} catch (CacheException e) {
			logger.error("Type: " + name + " does not exists in metamodel");
			throw new EntitiyRelationXMLLoadingException("Unknown RelationTypeName for this model :: " + name + " does not exists in metamodel");
		}
	}

	public EntityType getEntityTypeFromId(Long id) throws EntitiyRelationXMLLoadingException {

		EntityType type = entityTypeStore.get(id);

		if (type != null) {
			return type;
		} else {
			logger.error("Type: " + id + " does not exists in metamodel");
			throw new EntitiyRelationXMLLoadingException("Unknown EntityTypeId for this model db :: " + id + " does not exists in metamodel");
		}
	}

	public RelationType getRelationTypeFromId(Long id) throws EntitiyRelationXMLLoadingException {

		RelationType type = relationTypeStore.get(id);

		// if exists in map return cached value
		if (type != null)
			return type;
		else {
			logger.error("Type: " + id + " does not exists in metamodel");
			throw new EntitiyRelationXMLLoadingException("Unknown RelationTypeId for this model db :: " + id + " does not exists in metamodel");
		}
	}

	public EntityType getEntityTypeByEntityId(UUID entityId) throws EntitiyRelationXMLLoadingException {

		Long entityTypeId = entityRepository.getEntityTypeIdByEntityId(entityId);

		if (entityTypeId == null)
			throw new EntitiyRelationXMLLoadingException("Unknown EntityTypeID for this model db :: Entity: " + entityId + " does not exists");

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

	public FindOrCreateEntityResult findOrCreateFinalEntity(SourceEntity sourceEntity) {

		Collection<SemanticIdentifier> semanticIdentifiers = sourceEntity.getSemanticIdentifiers();
		List<Long> semanticIds = semanticIdentifiers.stream().map(SemanticIdentifier::getId)
				.collect(Collectors.toList());

		Boolean entityAlreadyExists = true;
		Entity entity = entityRepository.findEntityWithSemanticIdentifiers(semanticIds);

		if (entity == null) { // No entities with shared semantic identifiers exists the create
			entity = new Entity(sourceEntity.getEntityType());
			entityAlreadyExists = false;
		}

		entity.setDirty(true);
		entity.addSemanticIdentifiers(semanticIdentifiers);
		entityRepository.saveAndFlush(entity);

		return new FindOrCreateEntityResult(entity, entityAlreadyExists);
	}

	@Getter @Setter
	@AllArgsConstructor
	class FindOrCreateEntityResult {
		private Entity entity;
		private Boolean entityAlreadyExists;
	}

	@Transactional
	public synchronized void mergeEntityRelationData() {
		entityRepository.mergeEntiyRelationData();
	}

	public List<Entity> findEntitiesByProvenanceSourceAndRecordId(String sourceId, String recordId) {
		return entityRepository.findByProvenanceSourceAndRecordId(sourceId, recordId);
	}

    public Collection<Relation> getFromRelations(Entity entity, String relationName) throws EntitiyRelationXMLLoadingException {
		Long relationId = getRelationTypeFromName(relationName).getId();
		return entityRepository.getFromRelations(entity.getId(), relationId);
    }

    public Collection<Entity> getFromRelatedEntities(Entity entity, String relationName) throws EntitiyRelationXMLLoadingException {
		Long relationId = getRelationTypeFromName(relationName).getId();

		return entityRepository.getFromRelatedEntities(entity.getId(), relationId);
    }

	public Collection<Entity> getToRelatedEntities(Entity entity, String relationName) throws EntitiyRelationXMLLoadingException {
		Long relationId = getRelationTypeFromName(relationName).getId();
		return entityRepository.getToRelatedEntities(entity.getId(), relationId);
	}

	public Collection<Relation> getToRelations(Entity entity, String relationName) throws EntitiyRelationXMLLoadingException {
		Long relationId = getRelationTypeFromName(relationName).getId();
		return entityRepository.getToRelations(entity.getId(), relationId);
		
	}

//	public Page<Entity> findEntitiesByProvenanceSource(String sourceId, Pageable pageable) {
//		return entityRepository.findEntitiesByProvenaceSource(sourceId, pageable);
//	}

}