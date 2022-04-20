
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

package org.lareferencia.core.entity.indexing.vivo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.indexing.vivo.config.AttributeIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.vivo.config.NamespaceConfig;
import org.lareferencia.core.entity.indexing.vivo.config.OutputConfig;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig.TripleObject;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig.TriplePredicate;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig.TripleSubject;
import org.lareferencia.core.entity.indexing.vivo.config.RelationIndexingConfig;
import org.lareferencia.core.entity.services.EntityDataService;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class EntityIndexerRDFImpl implements IEntityIndexer {
	
	private static Logger logger = LogManager.getLogger(EntityIndexerRDFImpl.class);
	
	@Autowired
	EntityDataService entityDataService;
	
	private IndexingConfiguration indexingConfig;
	private Map<String, EntityIndexingConfig> configsByEntityType;
	private Map<String, String> namespaces;
	private List<OutputConfig> outputs;
	
	private Map<String, String> attributesForConcat;
	
	private static final String ENTITY_ID = "UUID";
	private static final String ATTR_VALUE = "$value";
	private static final String RELATION = "relation";
	private static final String TARGET_ENTITY = "target";
	private static final String NEW_ENTITY = "new";
	private static final String OBJECT_PROPERTY = "objectProperty";
	private static final String CONCATENATION = "$concat";
	
	private Dataset dataset;
	private Model m;

	@Override
	public void index(Entity entity) throws EntityIndexingException {
		
		try {
			String entityId = entity.getId().toString();
			EntityType type = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());
			EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(type.getName());

			if (entityIndexingConfig == null) {
				throw new EntityIndexingException("Error mapping entity: " + entity.getId() + " RDF mapping config for " + type.getName() + " EntityType not found");
			}
			
			//Map attributes
			attributesForConcat = new HashMap<String, String>();
			List<AttributeIndexingConfig> sourceAttributes = entityIndexingConfig.getSourceAttributes();
			Map<String, Collection<FieldOccurrence>> entityAttrOccurrences = entity.getOccurrencesAsMap();
			
			processAttributeList(sourceAttributes, entityAttrOccurrences, entityId, false);
			
			//Map relations
			Multimap<String, Relation> relationsMap = getRelationMultimap(entity);
			
			for (RelationIndexingConfig relationConfig : entityIndexingConfig.getSourceRelations()) {
				for (Relation relation : relationsMap.get(relationConfig.getName())) {
					String relationId = relation.getId().toString();
					Entity relatedEntity = relation.getRelatedEntity(entity.getId());
					String relatedEntityId = relatedEntity.getId().toString();
					List<RDFTripleConfig> triplesConfig = relationConfig.getTargetTriples();
					
					for (RDFTripleConfig tripleConfig : triplesConfig) {
						processTargetTriple(tripleConfig, null, entityId, relationId, relatedEntityId, null);
					}
					
					//Relation fields
					List<AttributeIndexingConfig> relSourceAttributes = relationConfig.getSourceAttributes();
					Map<String, Collection<FieldOccurrence>> relAttrOccurrences = relation.getOccurrencesAsMap();
					
					processAttributeList(relSourceAttributes, relAttrOccurrences, relationId, true);
				}
			}
			
		} catch (EntityRelationException e) {
			throw new EntityIndexingException("Indexing error. " + e.getMessage());
		}
	}

	@Override
	public void delete(String entityId) throws EntityIndexingException {
		// TODO Not yet implemented
		
	}

	@Override
	public void deleteAll(Collection<String> idList) throws EntityIndexingException {
		// TODO Not yet implemented
		
	}

	@Override
	public void flush() {

		//Save RDF model to one or more files
		List<OutputConfig> files = getOutputsByType("file");
		
		for (OutputConfig file : files) {
			String filePath = file.getPath() + file.getName();
			
			writeRDFModel(filePath, file.getFormat());
		}
	}

	@Override
	public void setConfig(String configFilePath) {
		
		try {
			indexingConfig = IndexingConfiguration.loadFromXml(configFilePath);
			outputs = indexingConfig.getOutputs();
			namespaces = new HashMap<String, String>();
			configsByEntityType = new HashMap<String, EntityIndexingConfig>();
			
			for (NamespaceConfig namespace : indexingConfig.getNamespaces()) {
				namespaces.put(namespace.getPrefix(), namespace.getUrl());
			}
			
			for (EntityIndexingConfig entityIndexingConfig: indexingConfig.getSourceEntities()){
				configsByEntityType.put(entityIndexingConfig.getType(), entityIndexingConfig);
			}
				
			logger.info("RDF Mapping Config File: " + configFilePath + " loaded.");
			
			try {
				String directory = getOutputsByType("triplestore").get(0).getPath();
				dataset = TDBFactory.createDataset(directory);
				
				//Clear the dataset in case there is data from a previous indexing
				clearDataset();
				
				logger.info("TDB triplestore created at " + directory);
			
			} catch (Exception e) {
				logger.error("No output of type 'triplestore' defined in the config file.");
			}
				
		} catch (Exception e) {
			logger.error("RDF Mapping Config File: " + configFilePath + e.getMessage());
		}
	}	

	
	private List<OutputConfig> getOutputsByType(String type) {
		
		List<OutputConfig> outputConfigs = new ArrayList<OutputConfig>();
		
		for (OutputConfig output : outputs) {
			if (output.getType().equalsIgnoreCase(type)) {
				outputConfigs.add(output);
			}
		}
		
		return outputConfigs;
	}
	
	private Multimap<String, Relation> getRelationMultimap(Entity entity) throws EntityRelationException {
		
		Multimap<String, Relation> relationsByName = ArrayListMultimap.create();
		
		for (Relation relation: entity.getFromRelations() ) {
			RelationType rtype = entityDataService.getRelationTypeFromId(relation.getRelationTypeId());			
			relationsByName.put(rtype.getName(), relation);
		}
		
		for (Relation relation: entity.getToRelations() ) {
			RelationType rtype = entityDataService.getRelationTypeFromId(relation.getRelationTypeId());			
			relationsByName.put(rtype.getName(), relation);
		}
				
		return relationsByName;
	}
	
	private void clearDataset() {
		
		dataset.begin(ReadWrite.WRITE);
		
		try {
			m = dataset.getDefaultModel();
			m.removeAll();
			dataset.commit();
		} finally { 
			dataset.end(); 
		}	
	}
	
	private String expandElementUri (String value) {
		
		String namespace = namespaces.get(value.split(":")[0]);
		String name = value.split(":")[1];
		
		return namespace + name;
	}
	
	private String buildIndividualUri (String namespace, String prefix, String id) {
		
		String ns = namespaces.get(namespace);
		prefix = prefix == null ? "" : prefix;
		
		return ns + prefix + id;
	}
	
	private String createRandomId() {
		
		return UUID.randomUUID().toString();
	}
	
	private String createResourceId(TripleSubject resource, String occrValue, 
			String entityId, String relationId, String relatedEntityId, String alternativeId) {
		
		String idSource = resource.getIdSource();
		String idType = resource.getIdType();
				
		String	sourceEntityId = (idSource != null && idSource.equals(RELATION)) ? relationId 
				: ((idSource != null && idSource.equals(TARGET_ENTITY) ? relatedEntityId 
				: ((idSource != null && idSource.equals(NEW_ENTITY)) ? alternativeId : entityId)));
		
		String resourceId;
		try {
			resourceId = idType.equals(ENTITY_ID) ? sourceEntityId 
					: (idType.equals(ATTR_VALUE) ? URLEncoder.encode(occrValue.replaceAll(" ", "_"), "UTF-8") 
					: createRandomId());
			
			return resourceId;

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
		
	}
	
	private void processAttributeList(List<AttributeIndexingConfig> attributeConfigs, 
								Map<String, Collection<FieldOccurrence>> occurrences, 
								String elementId, boolean fromRelation) {
		
		for (AttributeIndexingConfig attributeConfig : attributeConfigs) {	
			if (attributeConfig.getSubAttributes() != null) { //field has subfields
				List<AttributeIndexingConfig> subAttributes = attributeConfig.getSubAttributes();
				processAttributeList(subAttributes, occurrences, elementId, fromRelation);
			}
			
			if (occurrences.containsKey(attributeConfig.getName())) {
				for (FieldOccurrence occr : occurrences.get(attributeConfig.getName())) {
					try {
						String occrValue = occr.getValue();
						String alternativeId = createRandomId(); //in case no available id can be used
						List<RDFTripleConfig> triplesConfig = attributeConfig.getTargetTriples();

						for (RDFTripleConfig tripleConfig : triplesConfig) {
							if (fromRelation) {
								processTargetTriple(tripleConfig, occrValue, null, elementId, null, alternativeId);
							}
							else {
								processTargetTriple(tripleConfig, occrValue, elementId, null, null, alternativeId);
							}
						}
					} catch (EntityRelationException e) {
						logger.error("Error mapping attribute: " + attributeConfig.getName() + "::" + e.getMessage());
					}
				}
			}	
		}
	}
	
	private void processTargetTriple(RDFTripleConfig tripleConfig, String occrValue, 
			String entityId, String relationId, String relatedEntityId, String alternativeId) {

		TripleSubject subject = tripleConfig.getSubject();
		TriplePredicate predicate = tripleConfig.getPredicate();
		TripleObject object = tripleConfig.getObject();

		String subjectType = expandElementUri(subject.getType());
		String subjectId = createResourceId(subject, occrValue, entityId, relationId, relatedEntityId, alternativeId);
		String subjectValue = buildIndividualUri(subject.getNamespace(), subject.getPrefix(), subjectId);

		String predicateType = predicate.getType();
		String predicateValue = expandElementUri(predicate.getValue());

		String objectType = null;
		String objectValue = "";

		boolean wait = false;

		if (predicateType.equals(OBJECT_PROPERTY)) {
			String objectId = createResourceId(object, occrValue, entityId, relationId, relatedEntityId, alternativeId);
			objectType = expandElementUri(object.getType());
			objectValue = buildIndividualUri(object.getNamespace(), object.getPrefix(), objectId);
		}
		else { //data property
			if (object.getType() != null) {
				objectType = expandElementUri(object.getType());
			}

			//Check if the literal is a concatenation of attributes
			if (object.getValue().equals(CONCATENATION)) {
				attributesForConcat.put(object.getStoreAs(), occrValue);
				LinkedList<String> parts = Stream.of(object.getParts().split(";"))
						.collect(Collectors.toCollection(LinkedList::new));

				for (String part : parts) {
					if (part.startsWith("$")) { //an attribute value
						if (attributesForConcat.containsKey(part)) {
							objectValue += attributesForConcat.get(part);
						}
						else {
							wait = true; //part missing, wait until it is retrieved to build the triple
							break;
						}
					}
					else { //a constant
						objectValue += part;
					}
				}
			}
			else {
				objectValue = object.getValue().equals(ATTR_VALUE) ? occrValue : object.getValue();
			}	
		}

		//If the object value is a concatenation of attributes but not all values are available
		//at this point, wait until all necessary attributes are retrieved to create the triple
		if (!wait) {
			createRDFTriple(subjectValue, subjectType, predicateValue, predicateType, objectValue, objectType);
		}	
	}

	private void createRDFTriple(String subjectValue, String subjectType, 
								String predicateValue, String predicateType,
								String objectValue, String objectType) {

		dataset.begin(ReadWrite.WRITE);

		try {
			m = dataset.getDefaultModel();

			Resource subject;
			Property predicate;
			RDFNode object;

			//Create subject
			subject = m.createResource(subjectValue);
			m.add(subject, RDF.type, m.createResource(subjectType));

			//Create predicate
			predicate = m.createProperty(predicateValue);

			//Create object
			if (predicateType.equals(OBJECT_PROPERTY)) { //resource object
				object = m.createResource(objectValue);
				m.add(object.asResource(), RDF.type, m.createResource(objectType));
			}
			else { //literal object
				if (objectType == null) { //untyped literal
					object = m.createLiteral(objectValue);
				}
				else {
					object = m.createTypedLiteral(objectValue, objectType);
				}
			}

			//Create triple
			m.add(subject, predicate, object);

			//Commit the updates
			dataset.commit();

		} finally { 
			dataset.end(); 
		}
	}
	
	private void writeRDFModel(String outputFilePath, String format) {
		
		dataset.begin(ReadWrite.READ);

		try {
			m = dataset.getDefaultModel();
			
			// Write RDF file
			OutputStream writer = new FileOutputStream(outputFilePath);
			m.write(writer, format);
			writer.close();
		}
		catch (IOException e){
			e.printStackTrace();
		}
		finally { 
			dataset.end(); 
		}
	}

}
