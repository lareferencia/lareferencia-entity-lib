package org.lareferencia.core.entity.indexing.vivo;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.*;
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
import org.lareferencia.core.entity.services.CacheException;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityModelCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.util.*;

public abstract class AbstractEntityIndexerRDF implements IEntityIndexer {

    @Autowired
    EntityDataService entityDataService;

    @Autowired
    EntityModelCache entityModelCache;


    private static final String ENTITY_ID = "UUID";
    private static final String ATTR_VALUE = "$value";
    private static final String RELATION = "relation";
    private static final String TARGET_ENTITY = "target";
    private static final String NEW_ENTITY = "new";
    private static final String OBJECT_PROPERTY = "objectProperty";

    protected static Logger logger = LogManager.getLogger(AbstractEntityIndexerRDF.class);

    protected Map<String, EntityIndexingConfig> configsByEntityType;
    protected Map<String, String> namespaces;
    protected IndexingConfiguration indexingConfig;
    protected List<OutputConfig> outputs;
    protected String graph;
    
    
    protected boolean persist;
    protected Dataset dataset;
    protected Model model;

    @Autowired
    ApplicationContext context;

    // this will be used to filter out fields that are not to be indexed
    // will be injected by spring context on set config method
    FieldOccurrenceFilterService fieldOccurrenceFilterService;


    // open transaction
    public void openTransaction() {
        dataset.begin(ReadWrite.WRITE);
    }

    // try to commit it and close it
    public void commitAndCloseTransaction() {
        dataset.commit();
        dataset.end();
    }

    // abort the transaction
    public void abortTransaction() {
        dataset.abort();
        dataset.end();
    }
    

    @Transactional
    @Override
    public void index(Entity entity) throws EntityIndexingException {

        // if persist is true, then we try to open a transaction
        if (persist) {
            openTransaction();
        }

        try {
            entityDataService.addEntityToCache(entity);

            // Obtener el tipo de entidad y la configuración
            EntityType type = entityModelCache.getObjectById(EntityType.class, entity.getEntityTypeId());
            String entityTypeName = type.getName();
            EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(entityTypeName);

            if (entityIndexingConfig == null) {
                throw new EntityIndexingException("Error mapping entity: " + entity.getId() +
                        " RDF mapping config for " + entityTypeName + " EntityType not found");
            }

            String entityId = entity.getId().toString();

            // Map attributes
            List<AttributeIndexingConfig> sourceAttributes = entityIndexingConfig.getSourceAttributes();

            // if there are no attributes to index, then we skip the entity atrributes and occurences
            if (sourceAttributes != null && !sourceAttributes.isEmpty()) {
                entity.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                processAttributeList(sourceAttributes, entity);
            }

            for (RelationIndexingConfig relationConfig : entityIndexingConfig.getSourceRelations()) {

                String relationName = relationConfig.getName();
                Boolean isFromMember = entityModelCache.isFromRelation(relationName, entityTypeName);

                for (Relation relation : entityDataService.getRelationsWithThisEntityAsMember(entity.getId(), relationName, isFromMember)) {

                    String relationId = relation.getId().toString();
                    Entity relatedEntity = relation.getRelatedEntity(entity.getId());
                    String relatedEntityId = relatedEntity.getId().toString();

                    List<RDFTripleConfig> triplesConfig = relationConfig.getTargetTriples();
                    for (RDFTripleConfig tripleConfig : triplesConfig) {
                        processTargetTriple(tripleConfig, null, entityId, relationId, relatedEntityId, null);
                    }

                    // Relation fields
                    List<AttributeIndexingConfig> relSourceAttributes = relationConfig.getSourceAttributes();
                    if ( relSourceAttributes != null && !relSourceAttributes.isEmpty() ) {
                        processRelationAttributeList(relSourceAttributes, relation);
                    }

                }
            }

        } catch (CacheException | EntityRelationException e) { // Capturar ambas excepciones
            abortTransaction();
            throw new EntityIndexingException("Indexing error for entity: " + entity.getId() + ". " + e.getMessage());
        } finally {
            if (persist) {
                commitAndCloseTransaction();
            }
            
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
    public void flush() throws EntityIndexingException {
        if (persist) {
            commitAndCloseTransaction();
        }
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

        if (graph == null || graph.isEmpty()) { // Check for null as well
            model = dataset.getDefaultModel();
        }
        else {
            model = dataset.getNamedModel(graph);
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

        String sourceEntityId = alternativeId; // Default to alternativeId
        if (RELATION.equals(idSource)) {
            sourceEntityId = relationId;
        } else if (TARGET_ENTITY.equals(idSource)) {
            sourceEntityId = relatedEntityId;
        } else if (idSource != null) { //  idSource is not null, and it's not NEW_ENTITY, RELATION, or TARGET_ENTITY
            sourceEntityId = entityId;
        }

        // Using if-else if-else instead of switch
        if (ENTITY_ID.equals(idType)) {
            return sourceEntityId;
        } else if (ATTR_VALUE.equals(idType)) {
            return createNameBasedId(occrValue);
        } else {
            return createRandomId();
        }
    }

    private void processAttributeList(List<AttributeIndexingConfig> attributeConfigs, Entity entity) {

        String elementId = entity.getId().toString();

        for (AttributeIndexingConfig attributeConfig : attributeConfigs) {
            Collection<FieldOccurrence> fieldOccrs = entity.getFieldOccurrences(attributeConfig.getName());

            if (fieldOccrs == null || fieldOccrs.isEmpty()) continue;

            // Aplicar filtros (igual que en el indexador Elasticsearch)
            if (fieldOccurrenceFilterService != null && attributeConfig.getFilter() != null) {
                Map<String, String> filterParams = attributeConfig.getParams();
                filterParams.put("field", attributeConfig.getName());
                if (attributeConfig.getPreferredValuesOnly()) {
                    filterParams.put("preferred", "true");
                }
                //No existen subfields en el config del RDF
                fieldOccrs = fieldOccurrenceFilterService.filter(fieldOccrs, attributeConfig.getFilter(), filterParams);
            }

            for (FieldOccurrence fieldOccr : fieldOccrs) {
                try {
                    String occrValue;
                    if (!attributeConfig.getSubAttributes().isEmpty()) { // Tiene subcampos
                        List<AttributeIndexingConfig> subAttributes = attributeConfig.getSubAttributes();
                        for (AttributeIndexingConfig subAttribute : subAttributes) {
                            occrValue = fieldOccr.getValue(subAttribute.getName());
                            if (occrValue != null) {
                                processEntityAttribute(occrValue, subAttribute, elementId);
                            }
                        }
                    } else { // Campo simple
                        occrValue = fieldOccr.getValue();
                        processEntityAttribute(occrValue, attributeConfig, elementId);
                    }
                } catch (EntityRelationException e) {
                    logger.error("Error mapping attribute: " + attributeConfig.getName() + "::" + e.getMessage());
                }
            }
        }
    }

    private void processEntityAttribute (String occrValue, AttributeIndexingConfig attributeConfig, String elementId) {

        String alternativeId = createRandomId(); //in case no available id can be used
        List<RDFTripleConfig> triplesConfig = attributeConfig.getTargetTriples();

        for (RDFTripleConfig tripleConfig : triplesConfig) {
            processTargetTriple(tripleConfig, occrValue, elementId, null, null, alternativeId);
        }
    }

    private void processRelationAttributeList(List<AttributeIndexingConfig> attributeConfigs, Relation relation) {

        String elementId = relation.getId().toString();
        relation.loadOcurrences( entityModelCache.getNamesByIdMap(FieldType.class)); // Usar el cache

        for (AttributeIndexingConfig attributeConfig : attributeConfigs) {
            Collection<FieldOccurrence> fieldOccrs = relation.getFieldOccurrences(attributeConfig.getName());

            if (fieldOccrs == null || fieldOccrs.isEmpty()) continue;

            // Aplicar filtros (igual que en el indexador Elasticsearch)
            if (fieldOccurrenceFilterService != null && attributeConfig.getFilter() != null) {
                Map<String, String> filterParams = attributeConfig.getParams();
                filterParams.put("field", attributeConfig.getName());
                if (attributeConfig.getPreferredValuesOnly()) {
                    filterParams.put("preferred", "true");
                }
                //No existen subfields en el config del RDF
                fieldOccrs = fieldOccurrenceFilterService.filter(fieldOccrs, attributeConfig.getFilter(), filterParams);
            }

            for (FieldOccurrence fieldOccr : fieldOccrs) {
                try {
                    String occrValue;
                    if (!attributeConfig.getSubAttributes().isEmpty()) { // Tiene subcampos
                        List<AttributeIndexingConfig> subAttributes = attributeConfig.getSubAttributes();
                        for (AttributeIndexingConfig subAttribute : subAttributes) {
                            occrValue = fieldOccr.getValue(subAttribute.getName());
                            if (occrValue != null) {
                                processRelationAttribute(occrValue, subAttribute, elementId);
                            }
                        }
                    } else { // Campo simple
                        occrValue = fieldOccr.getValue();
                        processRelationAttribute(occrValue, attributeConfig, elementId);
                    }
                } catch (EntityRelationException e) {
                    logger.error("Error mapping attribute: " + attributeConfig.getName() + "::" + e.getMessage());
                }
            }
        }
    }

    private void processRelationAttribute (String occrValue, AttributeIndexingConfig attributeConfig, String elementId) {

        String alternativeId = createRandomId(); //in case no available id can be used
        List<RDFTripleConfig> triplesConfig = attributeConfig.getTargetTriples();

        for (RDFTripleConfig tripleConfig : triplesConfig) {
            processTargetTriple(tripleConfig, occrValue, null, elementId, null, alternativeId);
        }
    }

    private void processTargetTriple(RDFTripleConfig tripleConfig, String occrValue, String entityId, String relationId, String relatedEntityId, String alternativeId) {

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

        if (predicateType.equals(OBJECT_PROPERTY)) {
            String objectId = createResourceId(object, occrValue, entityId, relationId, relatedEntityId, alternativeId);
            objectType = expandElementUri(object.getType());
            objectValue = buildIndividualUri(object.getNamespace(), object.getPrefix(), objectId);
        }
        else { //data property
            if (object.getType() != null) {
                objectType = expandElementUri(object.getType());
            }

            objectValue = object.getValue().equals(ATTR_VALUE) ? occrValue : object.getValue();
        }

        // create triple
        createRDFTriple(subjectValue, subjectType, predicateValue, predicateType, objectValue, objectType);

    }

    private void createRDFTriple(String subjectValue, String subjectType,
                                 String predicateValue, String predicateType,
                                 String objectValue, String objectType) {
        // Simplificado:  Si persist es true, se asume que ya estamos en una transacción
        if (persist) {
            setTDBModel(); // Asegurarse de que el modelo está configurado
            assembleRDFTriple(subjectValue, subjectType, predicateValue, predicateType, objectValue, objectType);
        }
        else {
            // Write the triple to the in-memory RDF model
            if (model.isClosed()) {
                model = ModelFactory.createDefaultModel();
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
        subject = model.createResource(subjectValue);
        model.add(subject, RDF.type, model.createResource(subjectType));

        //Create predicate
        predicate = model.createProperty(predicateValue);

        //Create object
        if (predicateType.equals(OBJECT_PROPERTY)) { //resource object
            object = model.createResource(objectValue);
            model.add(object.asResource(), RDF.type, model.createResource(objectType));
        }
        else { //literal object
            if (objectType == null) { //untyped literal
                object = model.createLiteral(objectValue);
            }
            else {
                object = model.createTypedLiteral(objectValue, objectType);
            }
        }

        //Create triple
        model.add(subject, predicate, object);

        if (logger.isDebugEnabled()) {
            Model tempModel = ModelFactory.createDefaultModel();
            Statement stmt = tempModel.createStatement(subject, predicate, object);
            tempModel.add(stmt);

            StringWriter out = new StringWriter();
            tempModel.write(out, "TURTLE"); // Serializar a Turtle
            logger.debug("Tripleta: " + out.toString());
            tempModel.close();
        }
    }

}