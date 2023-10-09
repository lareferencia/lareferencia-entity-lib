package org.lareferencia.core.entity.indexing.vivo;

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
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.indexing.filters.FieldOccurrenceFilterService;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.indexing.vivo.config.AttributeIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.vivo.config.OutputConfig;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig.TripleObject;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig.TriplePredicate;
import org.lareferencia.core.entity.indexing.vivo.config.RDFTripleConfig.TripleSubject;
import org.lareferencia.core.entity.indexing.vivo.config.RelationIndexingConfig;
import org.lareferencia.core.entity.services.EntityDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public abstract class AbstractEntityIndexerRDF implements IEntityIndexer {
	
	@Autowired
	EntityDataService entityDataService;
	
	private Map<String, String> attributesForConcat;
	
	private static final String ENTITY_ID = "UUID";
	private static final String ATTR_VALUE = "$value";
	private static final String RELATION = "relation";
	private static final String TARGET_ENTITY = "target";
	private static final String NEW_ENTITY = "new";
	private static final String OBJECT_PROPERTY = "objectProperty";
	private static final String CONCATENATION = "$concat";
	
	protected static Logger logger = LogManager.getLogger(AbstractEntityIndexerRDF.class);
	
	protected Map<String, EntityIndexingConfig> configsByEntityType;
	protected Map<String, String> namespaces;
	protected IndexingConfiguration indexingConfig;
	protected List<OutputConfig> outputs;
	protected String graph;
	protected boolean persist;
	protected Dataset dataset;
	protected Model m;
	
	@Autowired
	ApplicationContext context;
	
	// this will be used to filter out fields that are not to be indexed
	// will be injected by spring context on set config method
	FieldOccurrenceFilterService fieldOccurrenceFilterService;

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

	}

	@Override
	public void setConfig(String configFilePath) {
		
	}	

	public List<OutputConfig> getOutputsByType(List<OutputConfig> outputs, String type) {
		
		List<OutputConfig> outputConfigs = new ArrayList<OutputConfig>();
		
		for (OutputConfig output : outputs) {
			if (output.getType().equalsIgnoreCase(type)) {
				outputConfigs.add(output);
			}
		}
		
		return outputConfigs;
	}
	
	public void setTDBModel() {
		
		if (graph.isEmpty()) {
			m = dataset.getDefaultModel();
		}
		else {
			m = dataset.getNamedModel(graph);
		}
	}
	
	/**
	 * loads the field occurrence filters from spring context and injects them into the service
	 */
	public void loadOccurFilters() {
		// Load dynamic field occurrence filters from spring context
		try {
			// get the service from spring context
			fieldOccurrenceFilterService = FieldOccurrenceFilterService.getServiceInstance(context);
			if ( fieldOccurrenceFilterService != null )
				// load the filters from spring context
				fieldOccurrenceFilterService.loadFiltersFromApplicationContext(context);

			logger.debug( "fieldOccurrenceFilterService: " + fieldOccurrenceFilterService.getFilters().toString() );
		} catch (Exception e) {
			logger.warn("Error loading field occurrence filters: " + e.getMessage());
		}
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
	
	private String createNameBasedId (String name) {
		
		return String.valueOf(name.hashCode());
	}
	
	private String createResourceId(TripleSubject resource, String occrValue, 
			String entityId, String relationId, String relatedEntityId, String alternativeId) {
		
		String idSource = resource.getIdSource();
		String idType = resource.getIdType();
				
		String	sourceEntityId = (idSource != null && idSource.equals(RELATION)) ? relationId 
				: ((idSource != null && idSource.equals(TARGET_ENTITY) ? relatedEntityId 
				: ((idSource != null && idSource.equals(NEW_ENTITY)) ? alternativeId : entityId)));
		
		String resourceId = idType.equals(ENTITY_ID) ? sourceEntityId 
					: (idType.equals(ATTR_VALUE) ? createNameBasedId(occrValue) 
					: createRandomId());
			
		return resourceId;	
	}
	
	private void processAttributeList(List<AttributeIndexingConfig> attributeConfigs, 
			Map<String, Collection<FieldOccurrence>> occurrences, 
			String elementId, boolean fromRelation) {

		for (AttributeIndexingConfig attributeConfig : attributeConfigs) {				
			if (occurrences.containsKey(attributeConfig.getName())) {
				Collection<FieldOccurrence> fieldOccrs = occurrences.get(attributeConfig.getName());
				
				// if field filter is defined and the services is available, apply it
				if ( fieldOccurrenceFilterService != null && attributeConfig.getFilter() != null ) {
					// get the params from the config
					Map<String, String> filterParams = attributeConfig.getParams();

					// add the field name to the params
					filterParams.put("field", attributeConfig.getName());
					
					// check if preferred flag is set and add it to the params
					if (attributeConfig.getPreferredValuesOnly())
						filterParams.put("preferred", "true");

					fieldOccrs = fieldOccurrenceFilterService.filter(fieldOccrs, attributeConfig.getFilter(), filterParams);
				}
				
				for (FieldOccurrence fieldOccr : fieldOccrs) {
					try {
						String occrValue;

						if (!attributeConfig.getSubAttributes().isEmpty()) { //field has subfields
							List<AttributeIndexingConfig> subAttributes = attributeConfig.getSubAttributes();
							
							for (AttributeIndexingConfig subAttribute : subAttributes) { //process each subfield
								occrValue = fieldOccr.getValue(subAttribute.getName());
								if (occrValue != null) {
									processAttribute(occrValue, subAttribute, elementId, fromRelation);
								}	
							}

						}
						else { //simple field
							occrValue = fieldOccr.getValue();
							processAttribute(occrValue, attributeConfig, elementId, fromRelation);
						}
					} catch (EntityRelationException e) {
						logger.error("Error mapping attribute: " + attributeConfig.getName() + "::" + e.getMessage());
					}
				}
			}	
		}
	}
	
	private void processAttribute (String occrValue, AttributeIndexingConfig attributeConfig,
			String elementId, boolean fromRelation) {

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

		if (persist) {
			// Write the triple to the TDB triplestore
			dataset.begin(ReadWrite.WRITE);
	
			try {
				setTDBModel();				
				assembleRDFTriple(subjectValue, subjectType, predicateValue, predicateType, objectValue, objectType);
				
				//Commit the updates
				dataset.commit();
	
			} finally { 
				dataset.end(); 
			}
		}
		else {
			// Write the triple to the in-memory RDF model
			if (m.isClosed()) {
				m = ModelFactory.createDefaultModel();
			}
			
			assembleRDFTriple(subjectValue, subjectType, predicateValue, predicateType, objectValue, objectType);
		}
	}
	
	private void assembleRDFTriple(String subjectValue, String subjectType, 
								String predicateValue, String predicateType,
								String objectValue, String objectType) {
		
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
	}

}
