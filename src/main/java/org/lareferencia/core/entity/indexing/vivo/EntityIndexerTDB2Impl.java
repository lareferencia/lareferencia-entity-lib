package org.lareferencia.core.entity.indexing.vivo;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.jena.query.Dataset;
// import org.apache.jena.query.QueryExecution; // No usado directamente en la última versión
// import org.apache.jena.query.QueryExecutionFactory; // No usado directamente en la última versión
// import org.apache.jena.query.QuerySolution; // No usado directamente en la última versión
import org.apache.jena.query.ReadWrite;
// import org.apache.jena.query.ResultSet; // No usado directamente en la última versión
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
// import org.apache.jena.rdf.model.StmtIterator; // No usado directamente en la última versión
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.indexing.filters.FieldOccurrenceFilterService;
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
import org.lareferencia.core.entity.services.CacheException;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityModelCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

public class EntityIndexerTDB2Impl implements IEntityIndexer, Closeable {

    @Autowired
    EntityDataService entityDataService;

    @Autowired
    EntityModelCache entityModelCache;

    @Autowired
    ApplicationContext context;

    FieldOccurrenceFilterService fieldOccurrenceFilterService;

    private static final String ENTITY_ID = "UUID";
    private static final String ATTR_VALUE = "$value";
    private static final String RELATION = "relation";
    private static final String TARGET_ENTITY = "target";
    private static final String NEW_ENTITY = "new";
    private static final String OBJECT_PROPERTY = "objectProperty";
    // private static final int PAGE_SIZE = 1000000; // No usado en la última versión de clearTDBStore

    protected static Logger logger = LogManager.getLogger(EntityIndexerTDB2Impl.class);

    protected Map<String, EntityIndexingConfig> configsByEntityType;
    protected Map<String, String> namespaces;
    protected IndexingConfiguration indexingConfig;
    
    protected Dataset dataset;
    protected Model model; // Este es el modelo principal para la indexación
    protected Model newTriplesModel; // Modelo temporal para las tripletas nuevas
    protected String graph; // Named graph URI
    protected boolean transactionActive = false; // Flag para controlar el estado de la transacción
    protected boolean hasTriplestore = false; // Flag para indicar si hay configuración de triplestore
    protected Map<String, Boolean> xmlFilesInitialized = new HashMap<>(); // Para rastrear archivos XML inicializados

    public EntityIndexerTDB2Impl() {
        // Constructor
    }

    @Override
    public void setConfig(String configFilePath) {
        try {
            indexingConfig = IndexingConfiguration.loadFromXml(configFilePath);
            List<OutputConfig> outputs = indexingConfig.getOutputs();
            namespaces = new HashMap<>();
            configsByEntityType = new HashMap<>();

            for (NamespaceConfig namespace : indexingConfig.getNamespaces()) {
                namespaces.put(namespace.getPrefix(), namespace.getUrl());
            }

            for (EntityIndexingConfig entityIndexingConfig : indexingConfig.getSourceEntities()) {
                configsByEntityType.put(entityIndexingConfig.getType(), entityIndexingConfig);
            }

            logger.info("RDF Mapping Config File: " + configFilePath + " loaded.");

            loadOccurFilters();

            // Buscar configuración de triplestore (opcional)
            List<OutputConfig> tdbOutputs = getOutputsByType(outputs, "triplestore");
            OutputConfig tdbConfig = null;
            
            if (!tdbOutputs.isEmpty()) {
                for (OutputConfig output : tdbOutputs) {
                    if ("triplestore".equalsIgnoreCase(output.getType())) {
                        tdbConfig = output;
                        break;
                    }
                }
            }
            
            if (tdbConfig != null) {
                // Configurar triplestore
                hasTriplestore = true;
                this.graph = tdbConfig.getGraph();
                String directory = tdbConfig.getPath();
                boolean reset = Boolean.parseBoolean(tdbConfig.getReset());

                dataset = TDBFactory.createDataset(directory);
                logger.info("Using TDB2 triplestore at " + directory + (this.graph != null && !this.graph.isEmpty() ? " with graph <" + this.graph + ">" : " with default graph"));

                if (reset) {
                    clearTDBStore();
                }
                
                setModelFromDataset(); // Inicializa this.model para operaciones de indexación
            } else {
                // No hay triplestore, usar modelo en memoria
                hasTriplestore = false;
                model = ModelFactory.createDefaultModel();
                model.setNsPrefixes(this.namespaces);
                logger.info("No triplestore configured. Using in-memory model for RDF generation.");
            }
            
            // Inicializar modelo para tripletas nuevas
            newTriplesModel = ModelFactory.createDefaultModel();
            newTriplesModel.setNsPrefixes(this.namespaces);
            
            // Inicializar el mapa de archivos XML
            xmlFilesInitialized = new HashMap<>();

        } catch (Exception e) {
            logger.error("Error setting RDF Mapping Config File: " + configFilePath + ". " + e.getMessage(), e);
            throw new RuntimeException("Configuration failed for EntityIndexerTDB2Impl", e);
        }
    }
    
    private void setModelFromDataset() {
        if (dataset == null) {
            throw new IllegalStateException("Dataset not initialized. Call setConfig first.");
        }
        
        boolean mustEndTransaction = false;
        if (!dataset.isInTransaction()) {
            dataset.begin(ReadWrite.READ); 
            mustEndTransaction = true;
        }
        try {
            if (this.graph == null || this.graph.isEmpty()) {
                this.model = dataset.getDefaultModel();
            } else {
                this.model = dataset.getNamedModel(this.graph);
            }
        } finally {
            if (mustEndTransaction && dataset.isInTransaction()) {
                dataset.end(); 
            }
        }
    }

    private void beginTransaction() {
        if (dataset == null) {
            throw new IllegalStateException("Dataset not initialized.");
        }
        if (!dataset.isInTransaction()) {
            dataset.begin(ReadWrite.WRITE);
            transactionActive = true;
        }
    }

    private void commitAndEndTransaction() {
        if (dataset == null) return; 
        if (dataset.isInTransaction()) {
            try {
                dataset.commit();
            } finally {
                dataset.end();
                transactionActive = false;
            }
        }
    }

    private void abortAndEndTransaction() {
        if (dataset == null) return; 
        if (dataset.isInTransaction()) {
            try {
                dataset.abort();
            } finally {
                dataset.end();
                transactionActive = false;
            }
        }
    }

    @Override
    public void prePage() throws EntityIndexingException {
        if (hasTriplestore) {
            beginTransaction();
        }
    }
    
    @Transactional 
    @Override
    public void index(Entity entity) throws EntityIndexingException {
        // Ya no maneja transacciones aquí, asume que ya hay una activa
        if (!transactionActive) {
            throw new EntityIndexingException("No active transaction. Call prePage() first.");
        }
        
        try {
            entityDataService.addEntityToCache(entity);

            EntityType type = entityModelCache.getObjectById(EntityType.class, entity.getEntityTypeId());
            String entityTypeName = type.getName();
            EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(entityTypeName);

            if (entityIndexingConfig == null) {
                throw new EntityIndexingException("Error mapping entity: " + entity.getId() +
                        " RDF mapping config for " + entityTypeName + " EntityType not found");
            }

            String entityId = entity.getId().toString();
            List<AttributeIndexingConfig> sourceAttributes = entityIndexingConfig.getSourceAttributes();

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

                    List<AttributeIndexingConfig> relSourceAttributes = relationConfig.getSourceAttributes();
                    if (relSourceAttributes != null && !relSourceAttributes.isEmpty()) {
                        processRelationAttributeList(relSourceAttributes, relation);
                    }
                }
            }
        } catch (CacheException | EntityRelationException e) {
            throw new EntityIndexingException("Indexing error for entity: " + entity.getId() + ". " + e.getMessage());
        } catch (Exception e) { 
            throw new EntityIndexingException("Unexpected error indexing entity: " + entity.getId() + ". " + e.getMessage());  
        }
    }

    @Override
    public void delete(String entityId) throws EntityIndexingException {
        logger.warn("Delete operation not yet implemented for TDB2Indexer.");
    }

    @Override
    public void deleteAll(Collection<String> idList) throws EntityIndexingException {
        logger.warn("DeleteAll operation not yet implemented for TDB2Indexer.");
    }

    @Override
    public void flush() throws EntityIndexingException {
        logger.info("Flushing data to configured outputs.");
        
        try {
            // Solo hacer commit al TDB si hay triplestore configurado
            if (hasTriplestore && transactionActive && dataset != null && dataset.isInTransaction()) {
                commitAndEndTransaction();
            }
            
            // Export a archivos de tipo "file" si están configurados
            if (newTriplesModel.size() > 0) {
                List<OutputConfig> outputs = indexingConfig.getOutputs();
                List<OutputConfig> files = getOutputsByType(outputs, "file");
                
                if (!files.isEmpty()) {
                    for (OutputConfig file : files) {
                        String filePath = file.getPath() + file.getName();
                        writeNewTriplesToFile(filePath, file.getFormat());
                    }
                } else {
                    logger.debug("No file outputs configured for export.");
                }
                
                // Clear the new triples model after export
                newTriplesModel.removeAll();
                
                // Si no hay triplestore, también limpiar el modelo principal
                if (!hasTriplestore) {
                    model.removeAll();
                }
            } else {
                logger.debug("No new triples to export to files.");
            }
        } catch (Exception e) {
            // En caso de error, abortar la transacción solo si hay triplestore
            if (hasTriplestore && transactionActive) {
                abortAndEndTransaction();
            }
            throw new EntityIndexingException("Error during flush: " + e.getMessage());
        }
    }
    
    private void writeNewTriplesToFile(String outputFilePath, String format) throws EntityIndexingException {
        try {
            if ("RDF/XML".equalsIgnoreCase(format) || "XML".equalsIgnoreCase(format)) {
                writeXMLTriplesToFile(outputFilePath, format);
            } else {
                // Para otros formatos, usar append normal
                try (FileOutputStream writer = new FileOutputStream(outputFilePath, true)) {
                    newTriplesModel.write(writer, format);
                    logger.info("New RDF triples (" + newTriplesModel.size() + ") exported to: " + outputFilePath + " in format: " + format);
                }
            }
        } catch (Exception e) {
            throw new EntityIndexingException("Error writing new triples to file: " + outputFilePath + " :: " + e.getMessage());
        }
    }
    
    private void writeXMLTriplesToFile(String outputFilePath, String format) throws EntityIndexingException {
        try {
            boolean isFirstWrite = !xmlFilesInitialized.getOrDefault(outputFilePath, false);
            
            if (isFirstWrite) {
                // Primera escritura: crear archivo con encabezado XML y las tripletas
                try (FileOutputStream writer = new FileOutputStream(outputFilePath, false)) {
                    // Crear un modelo temporal con namespaces para generar XML completo
                    Model tempModel = ModelFactory.createDefaultModel();
                    tempModel.setNsPrefixes(this.namespaces);
                    tempModel.add(newTriplesModel);
                    
                    // Escribir el XML completo pero lo vamos a modificar para dejarlo abierto
                    java.io.StringWriter stringWriter = new java.io.StringWriter();
                    tempModel.write(stringWriter, format);
                    String xmlContent = stringWriter.toString();
                    
                    // Remover la etiqueta de cierre para dejar el XML abierto
                    String openXml = xmlContent.replaceAll("</rdf:RDF>\\s*$", "");
                    
                    writer.write(openXml.getBytes("UTF-8"));
                    tempModel.close();
                    
                    xmlFilesInitialized.put(outputFilePath, true);
                    logger.info("XML file initialized with " + newTriplesModel.size() + " triples: " + outputFilePath);
                }
            } else {
                // Escrituras subsiguientes: agregar solo las tripletas sin encabezados
                try (FileOutputStream writer = new FileOutputStream(outputFilePath, true)) {
                    // Crear modelo temporal solo con las nuevas tripletas
                    Model tempModel = ModelFactory.createDefaultModel();
                    tempModel.add(newTriplesModel);
                    
                    // Generar XML y extraer solo el contenido de las tripletas
                    java.io.StringWriter stringWriter = new java.io.StringWriter();
                    tempModel.write(stringWriter, format);
                    String xmlContent = stringWriter.toString();
                    
                    // Extraer solo el contenido entre las etiquetas rdf:RDF
                    String bodyContent = extractXMLBody(xmlContent);
                    
                    if (!bodyContent.trim().isEmpty()) {
                        writer.write(bodyContent.getBytes("UTF-8"));
                    }
                    
                    tempModel.close();
                    logger.info("Added " + newTriplesModel.size() + " triples to existing XML file: " + outputFilePath);
                }
            }
        } catch (Exception e) {
            throw new EntityIndexingException("Error writing XML triples to file: " + outputFilePath + " :: " + e.getMessage());
        }
    }
    
    private String extractXMLBody(String xmlContent) {
        // Buscar el contenido entre <rdf:RDF...> y </rdf:RDF>
        int startIndex = xmlContent.indexOf('>');
        int endIndex = xmlContent.lastIndexOf("</rdf:RDF>");
        
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return xmlContent.substring(startIndex + 1, endIndex).trim();
        }
        
        return "";
    }
    
    private void finalizeXMLFiles() {
        // Cerrar todos los archivos XML abiertos
        List<OutputConfig> outputs = indexingConfig.getOutputs();
        List<OutputConfig> files = getOutputsByType(outputs, "file");
        
        for (OutputConfig file : files) {
            String format = file.getFormat();
            if (("RDF/XML".equalsIgnoreCase(format) || "XML".equalsIgnoreCase(format)) && 
                xmlFilesInitialized.getOrDefault(file.getPath() + file.getName(), false)) {
                
                try {
                    String filePath = file.getPath() + file.getName();
                    try (FileOutputStream writer = new FileOutputStream(filePath, true)) {
                        writer.write("\n</rdf:RDF>".getBytes("UTF-8"));
                        logger.info("Finalized XML file: " + filePath);
                    }
                } catch (Exception e) {
                    logger.error("Error finalizing XML file: " + file.getPath() + file.getName() + " :: " + e.getMessage());
                }
            }
        }
    }

    private void clearTDBStore() {
        logger.info("Reset option set to true. Clearing graph in TDB2 store...");
        
        if (dataset == null) {
            logger.error("Dataset is null, cannot clear TDB store.");
            return;
        }

        dataset.begin(ReadWrite.WRITE);
        Model localModelToClear; // Use a local model variable for clarity within this operation
        try {
            if (this.graph == null || this.graph.isEmpty()) {
                localModelToClear = dataset.getDefaultModel();
            } else {
                localModelToClear = dataset.getNamedModel(this.graph);
            }
            
            long totalTriples = localModelToClear.size(); 
            logger.info("Initial triples in graph: " + totalTriples);

            if (totalTriples == 0) {
                logger.info("Graph is already empty.");
                dataset.commit(); 
                return;
            }
            
            localModelToClear.removeAll();
            dataset.commit();
            logger.info("Graph cleared.");

        } catch (Exception e) {
            logger.error("Error clearing TDB2 graph: " + e.getMessage(), e);
            if (dataset.isInTransaction()) { // Check before aborting
                dataset.abort();
            }
        } finally {
            if (dataset.isInTransaction()) { 
                 dataset.end();
            }
        }
        logger.info("Graph clearing process finished.");
    }
    
    public List<OutputConfig> getOutputsByType(List<OutputConfig> outputs, String type) {
        List<OutputConfig> outputConfigs = new ArrayList<>();
        if (outputs == null) return outputConfigs;
        for (OutputConfig output : outputs) {
            if (output.getType().equalsIgnoreCase(type)) {
                outputConfigs.add(output);
            }
        }
        return outputConfigs;
    }

    public void loadOccurFilters() {
        try {
            fieldOccurrenceFilterService = FieldOccurrenceFilterService.getServiceInstance(context);
            if (fieldOccurrenceFilterService != null) {
                fieldOccurrenceFilterService.loadFiltersFromApplicationContext(context);
                logger.debug("fieldOccurrenceFilterService filters loaded: " + fieldOccurrenceFilterService.getFilters().toString());
            }
        } catch (Exception e) {
            logger.warn("Error loading field occurrence filters: " + e.getMessage(), e);
        }
    }

    private String expandElementUri(String value) {
        if (value == null || value.trim().isEmpty() || !value.contains(":")) return value; 
        String[] parts = value.split(":", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) return value; // Handle cases like "prefix:" or " :local"
        String namespaceURI = namespaces.get(parts[0]);
        String localName = parts[1];
        return (namespaceURI != null ? namespaceURI : parts[0] + ":") + localName; 
    }

    private String buildIndividualUri(String namespaceKey, String prefix, String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warn("Cannot build individual URI with null or empty ID. NamespaceKey: " + namespaceKey + ", Prefix: " + prefix);
            return "urn:uuid:" + createRandomId(); // Fallback to a random URN
        }
        String ns = namespaces.get(namespaceKey);
        if (ns == null) {
            logger.warn("Namespace not found for key: " + namespaceKey + ". Using key as URI part.");
            ns = namespaceKey + (namespaceKey.endsWith("/") || namespaceKey.endsWith("#") ? "" : "/"); 
        }
        prefix = prefix == null ? "" : prefix;
        return ns + prefix + id;
    }

    private String createRandomId() {
        return UUID.randomUUID().toString();
    }

    private String createNameBasedId(String name) {
        if (name == null) {
            logger.warn("Cannot create name-based ID from null name. Generating random ID.");
            return createRandomId();
        }
        return String.valueOf(name.hashCode()).replace("-", "_"); 
    }

    private String createResourceId(TripleSubject resource, String occrValue,
                                    String entityId, String relationId, String relatedEntityId, String alternativeId) {
        String idSource = resource.getIdSource();
        String idType = resource.getIdType();
        String sourceEntityId = null; // Initialize to null

        if (RELATION.equals(idSource)) {
            sourceEntityId = relationId;
        } else if (TARGET_ENTITY.equals(idSource)) {
            sourceEntityId = relatedEntityId;
        } else if (NEW_ENTITY.equals(idSource)) {
            sourceEntityId = alternativeId; 
        } else { 
            sourceEntityId = entityId;
        }

        if (sourceEntityId == null && !ATTR_VALUE.equals(idType)) {
             logger.warn("Source entity ID is null for resource creation (idSource: " + idSource + ", idType: " + idType + "). Using random ID.");
             return createRandomId();
        }
        
        if (ENTITY_ID.equals(idType)) {
            return sourceEntityId; // Can be null if the source (entityId, relationId etc) was null
        } else if (ATTR_VALUE.equals(idType)) {
            if (occrValue == null) { 
                 logger.warn("ATTR_VALUE specified for ID type, but occurrence value is null. Using random ID.");
                 return createRandomId();
            }
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

            if (fieldOccurrenceFilterService != null && attributeConfig.getFilter() != null) {
                Map<String, String> filterParams = new HashMap<>(attributeConfig.getParams()); 
                filterParams.put("field", attributeConfig.getName());
                if (attributeConfig.getPreferredValuesOnly()) {
                    filterParams.put("preferred", "true");
                }
                fieldOccrs = fieldOccurrenceFilterService.filter(fieldOccrs, attributeConfig.getFilter(), filterParams);
            }

            for (FieldOccurrence fieldOccr : fieldOccrs) {
                try {
                    if (!attributeConfig.getSubAttributes().isEmpty()) {
                        for (AttributeIndexingConfig subAttribute : attributeConfig.getSubAttributes()) {
                            String occrValue = fieldOccr.getValue(subAttribute.getName());
                            if (occrValue != null) {
                                processEntityAttribute(occrValue, subAttribute, elementId);
                            }
                        }
                    } else {
                        String occrValue = fieldOccr.getValue();
                         if (occrValue != null) { 
                            processEntityAttribute(occrValue, attributeConfig, elementId);
                        }
                    }
                } catch (EntityRelationException e) {
                    logger.error("Error processing attribute for entity " + elementId + ": " + attributeConfig.getName() + "::" + e.getMessage(), e);
                }
            }
        }
    }

    private void processEntityAttribute(String occrValue, AttributeIndexingConfig attributeConfig, String elementId) {
        String alternativeId = createRandomId();
        List<RDFTripleConfig> triplesConfig = attributeConfig.getTargetTriples();
        for (RDFTripleConfig tripleConfig : triplesConfig) {
            processTargetTriple(tripleConfig, occrValue, elementId, null, null, alternativeId);
        }
    }

    private void processRelationAttributeList(List<AttributeIndexingConfig> attributeConfigs, Relation relation) {
        String elementId = relation.getId().toString(); 
        relation.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));

        for (AttributeIndexingConfig attributeConfig : attributeConfigs) {
            Collection<FieldOccurrence> fieldOccrs = relation.getFieldOccurrences(attributeConfig.getName());
            if (fieldOccrs == null || fieldOccrs.isEmpty()) continue;

            if (fieldOccurrenceFilterService != null && attributeConfig.getFilter() != null) {
                 Map<String, String> filterParams = new HashMap<>(attributeConfig.getParams()); 
                filterParams.put("field", attributeConfig.getName());
                if (attributeConfig.getPreferredValuesOnly()) {
                    filterParams.put("preferred", "true");
                }
                fieldOccrs = fieldOccurrenceFilterService.filter(fieldOccrs, attributeConfig.getFilter(), filterParams);
            }

            for (FieldOccurrence fieldOccr : fieldOccrs) {
                try {
                    if (!attributeConfig.getSubAttributes().isEmpty()) {
                        for (AttributeIndexingConfig subAttribute : attributeConfig.getSubAttributes()) {
                            String occrValue = fieldOccr.getValue(subAttribute.getName());
                            if (occrValue != null) {
                                processRelationAttribute(occrValue, subAttribute, elementId);
                            }
                        }
                    } else {
                        String occrValue = fieldOccr.getValue();
                        if (occrValue != null) { 
                           processRelationAttribute(occrValue, attributeConfig, elementId);
                        }
                    }
                } catch (EntityRelationException e) {
                    logger.error("Error processing attribute for relation " + elementId + ": " + attributeConfig.getName() + "::" + e.getMessage(), e);
                }
            }
        }
    }

    private void processRelationAttribute(String occrValue, AttributeIndexingConfig attributeConfig, String relationId) {
        String alternativeId = createRandomId();
        List<RDFTripleConfig> triplesConfig = attributeConfig.getTargetTriples();
        for (RDFTripleConfig tripleConfig : triplesConfig) {
            processTargetTriple(tripleConfig, occrValue, null, relationId, null, alternativeId);
        }
    }

    private void processTargetTriple(RDFTripleConfig tripleConfig, String occrValue,
                                     String entityId, String relationId, String relatedEntityId, String alternativeId) {
        TripleSubject subjectConfig = tripleConfig.getSubject();
        TriplePredicate predicateConfig = tripleConfig.getPredicate();
        TripleObject objectConfig = tripleConfig.getObject();

        String subjectIdRaw = createResourceId(subjectConfig, occrValue, entityId, relationId, relatedEntityId, alternativeId);
        if (subjectIdRaw == null) {
            logger.warn("Generated subject ID is null. Skipping triple for subject config: " + subjectConfig.getNamespace() + ":" + subjectConfig.getPrefix() + " / " + subjectConfig.getType());
            return;
        }
        String subjectUri = buildIndividualUri(subjectConfig.getNamespace(), subjectConfig.getPrefix(), subjectIdRaw);
        String subjectTypeUri = expandElementUri(subjectConfig.getType());


        String predicateUri = expandElementUri(predicateConfig.getValue());
        String predicateType = predicateConfig.getType(); 

        if (predicateUri == null || predicateUri.trim().isEmpty()) {
            logger.warn("Predicate URI is null or empty. Skipping triple for subject: " + subjectUri);
            return;
        }

        String objectValue;
        String objectTypeUri = null; 

        if (OBJECT_PROPERTY.equals(predicateType)) {
            String objectIdRaw = createResourceId(objectConfig, occrValue, entityId, relationId, relatedEntityId, alternativeId);
            if (objectIdRaw == null) {
                logger.warn("Generated object ID is null for an object property. Skipping triple. Subject: " + subjectUri + ", Predicate: " + predicateUri);
                return;
            }
            objectTypeUri = expandElementUri(objectConfig.getType());
            objectValue = buildIndividualUri(objectConfig.getNamespace(), objectConfig.getPrefix(), objectIdRaw);
        } else { 
            if (objectConfig.getType() != null) { 
                objectTypeUri = expandElementUri(objectConfig.getType());
            }
            objectValue = ATTR_VALUE.equals(objectConfig.getValue()) ? occrValue : objectConfig.getValue();
            if (objectValue == null && ATTR_VALUE.equals(objectConfig.getValue())) {
                 logger.debug("Occurrence value is null for a data property, skipping triple generation for subject: " + subjectUri + ", predicate: " + predicateUri);
                 return; 
            }
        }
        
        if (objectValue == null) { 
            logger.warn("Object value is null for triple. Subject: " + subjectUri + ", Predicate: " + predicateUri + ". Skipping triple.");
            return;
        }

        assembleRDFTriple(subjectUri, subjectTypeUri, predicateUri, predicateType, objectValue, objectTypeUri);
    }

    private void assembleRDFTriple(String subjectUri, String subjectTypeUri,
                                   String predicateUri, String predicateType, 
                                   String objectValue, String objectTypeUri) { 
        if (this.model == null) {
            logger.error("Model is not initialized. Cannot add triple.");
            throw new IllegalStateException("RDF Model not initialized. Call setConfig first.");
        }
        
        // Solo verificar transacción si hay triplestore
        if (hasTriplestore && (!transactionActive || !dataset.isInTransaction())) {
             logger.error("Attempting to add triple outside of a transaction. Subject: " + subjectUri);
             throw new IllegalStateException("Cannot add triple: Not in a transaction.");
        }

        Resource subject = model.createResource(subjectUri);
        if (subjectTypeUri != null && !subjectTypeUri.trim().isEmpty()) { 
             model.add(subject, RDF.type, model.createResource(subjectTypeUri));
        }

        Property predicate = model.createProperty(predicateUri);
        RDFNode object;

        if (OBJECT_PROPERTY.equals(predicateType)) {
            object = model.createResource(objectValue);
            if (objectTypeUri != null && !objectTypeUri.trim().isEmpty()) { 
                model.add(object.asResource(), RDF.type, model.createResource(objectTypeUri));
            }
        } else { 
            if (objectTypeUri != null && !objectTypeUri.trim().isEmpty()) {
                object = model.createTypedLiteral(objectValue, objectTypeUri);
            } else {
                object = model.createLiteral(objectValue); 
            }
        }

        Statement stmt = model.createStatement(subject, predicate, object);
        model.add(stmt);
        
        // Also add to new triples model for export
        Statement newStmt = newTriplesModel.createStatement(
            newTriplesModel.createResource(subjectUri), 
            newTriplesModel.createProperty(predicateUri), 
            object instanceof Resource ? 
                newTriplesModel.createResource(objectValue) : 
                (objectTypeUri != null && !objectTypeUri.trim().isEmpty() ? 
                    newTriplesModel.createTypedLiteral(objectValue, objectTypeUri) :
                    newTriplesModel.createLiteral(objectValue))
        );
        newTriplesModel.add(newStmt);
        
        // Add type statements to new triples model too
        if (subjectTypeUri != null && !subjectTypeUri.trim().isEmpty()) {
            newTriplesModel.add(newTriplesModel.createResource(subjectUri), 
                               RDF.type, 
                               newTriplesModel.createResource(subjectTypeUri));
        }
        
        if (OBJECT_PROPERTY.equals(predicateType) && objectTypeUri != null && !objectTypeUri.trim().isEmpty()) {
            newTriplesModel.add(newTriplesModel.createResource(objectValue), 
                               RDF.type, 
                               newTriplesModel.createResource(objectTypeUri));
        }

        if (logger.isDebugEnabled()) {
            StringWriter out = new StringWriter();
            Model tempModel = ModelFactory.createDefaultModel();
            tempModel.setNsPrefixes(this.namespaces);
            tempModel.add(stmt);
            tempModel.write(out, "TURTLE");
            logger.debug("Added Triple: " + out.toString().trim());
            tempModel.close();
        }
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing EntityIndexerTDB2Impl.");
        try {
            // Solo hacer flush si hay datos pendientes
            if (hasTriplestore && dataset != null && dataset.isInTransaction()) {
                flush(); 
            }
            
            // Finalizar archivos XML
            finalizeXMLFiles();
            
        } catch (EntityIndexingException e) {
            logger.error("Error flushing data during close: " + e.getMessage(), e);
        } finally {
            if (hasTriplestore && dataset != null) {
                if (dataset.isInTransaction()) { 
                    logger.warn("Transaction was still active during close. Attempting to end.");
                    dataset.end();
                }
                dataset.close();
                logger.info("TDB2 Dataset closed.");
            }
        }
    }
    
}
