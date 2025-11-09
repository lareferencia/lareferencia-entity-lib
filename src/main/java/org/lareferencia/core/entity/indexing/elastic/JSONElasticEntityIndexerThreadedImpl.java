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

package org.lareferencia.core.entity.indexing.elastic;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.indexing.filters.FieldOccurrenceFilterService;
import org.lareferencia.core.entity.indexing.nested.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityModelCache;
import org.lareferencia.core.entity.services.exception.EntitiyRelationXMLLoadingException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import javax.net.ssl.SSLContext;

public class JSONElasticEntityIndexerThreadedImpl implements IEntityIndexer, Closeable {

    public static String ELASTIC_PARAM_PREFIX = "elastic-param-";
    private static String MAPPING_PROPERTIES_STR = "properties";
    private static String ID_FIELD = "id";
    private static String ID_FIELD_TYPE = "keyword";

    protected static Logger logger = LogManager.getLogger(JSONElasticEntityIndexerThreadedImpl.class);

    @Autowired
    EntityDataService entityDataService;

    @Autowired
    EntityModelCache entityModelCache;

    @Autowired
    ApplicationContext context;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Configuração Elasticsearch
    @Value("${elastic.host:localhost}")
    private String host;

    @Value("${elastic.port:9200}")
    private Integer port;

    @Value("${elastic.username:admin}")
    private String username;

    @Value("${elastic.password:admin}")
    private String password;

    @Value("${elastic.useSSL:false}")
    private Boolean useSSL;

    @Value("${elastic.authenticate:false}")
    private Boolean authenticate;

    @Value("${elastic.indexer.max.retries:10}")
    private int maxRetries;
    
    @Value("${elastic.indexer.circuit.breaker.max.failures:10}")
    private int circuitBreakerMaxFailures;
    
    @Value("${elastic.indexer.circuit.breaker.reset.timeout.ms:60000}")
    private long circuitBreakerResetTimeoutMs;
    
    @Value("${elastic.indexer.max.concurrent.tasks:0}")
    private int maxConcurrentTasksConfig; // 0 = auto-calculate

    private IndexingConfiguration indexingConfiguration;
    private String indexingConfigFilename;
    private Map<String, EntityIndexingConfig> configsByEntityType;
    private ObjectMapper jsonMapper;
    private RestHighLevelClient elasticClient = null;
    private FieldOccurrenceFilterService fieldOccurrenceFilterService;

    // --- THREADING COMPONENTS ---
    private ExecutorService indexingExecutor; // For entity processing threads

    private final Phaser activeIndexingPhaser = new Phaser(1); // Tracks producer tasks
    private volatile boolean shutdown = false;
    private final Object flushLock = new Object();
    
    // Configuração de threading
    private int indexingThreads = Runtime.getRuntime().availableProcessors();
    
    // Variables de threading (inicializadas en initializeThreading() con valores de configuración)
    private int maxConcurrentTasks;
    private Semaphore concurrentTasksSemaphore;

    // Stats
    private final AtomicLong documentsProduced = new AtomicLong(0);
    private final AtomicLong documentsIndexed = new AtomicLong(0);
    private final AtomicLong documentsFailedPermanently = new AtomicLong(0);
    
    // Circuit Breaker para Elasticsearch
    private ElasticCircuitBreaker circuitBreaker;
    
    public JSONElasticEntityIndexerThreadedImpl() {
        // Constructor
    }

    @Override
    public void setConfig(String configFilePath) throws EntityIndexingException {
        try {
            logger.info("Loading indexing config from: " + configFilePath);
            
            this.indexingConfigFilename = configFilePath;
            indexingConfiguration = IndexingConfiguration.loadFromXml(configFilePath);
            
            logger.info("Processing Elastic Indexer Config File: " + indexingConfigFilename);

            // load filters for field occurrence filtering
            loadOccurFilters();

            // build elastic client
            elasticClient = buildElasticRestClient();

            // create index mappings
            createIndexMappings();

            // create map of entity types to indexing configs
            configsByEntityType = new HashMap<String, EntityIndexingConfig>();
            for (EntityIndexingConfig entityIndexingConfig : indexingConfiguration.getEntityIndices())
                configsByEntityType.put(entityIndexingConfig.getEntityType(), entityIndexingConfig);

            // create json mapper with custom serializer for JSONEntityElastic
            jsonMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer(JSONEntityElastic.class, new JSONEntityElasticSerializer());
            jsonMapper.registerModule(module);

            // Inicializar threading
            initializeThreading();

            logger.info("Elastic Indexer Config File: " + indexingConfigFilename + " processed successfully");

        } catch (Exception e) {
            logger.error("Error setting Elastic Indexer Config File: " + configFilePath + ". " + e.getMessage(), e);
            throw new EntityIndexingException("Configuration failed for JSONElasticEntityIndexerThreadedImpl: " + e.getMessage());
        }
    }

    private void initializeThreading() {
        // Aplicar configuración externalizada con valores por defecto
        this.maxConcurrentTasks = maxConcurrentTasksConfig > 0 ? maxConcurrentTasksConfig : (indexingThreads * 2);
        
        this.indexingExecutor = Executors.newFixedThreadPool(indexingThreads);
        
        // Inicializar semáforo para limitar tareas concurrentes
        this.concurrentTasksSemaphore = new Semaphore(maxConcurrentTasks);
        
        // Inicializar Circuit Breaker para Elasticsearch con configuración externalizada
        this.circuitBreaker = new ElasticCircuitBreaker(circuitBreakerMaxFailures, circuitBreakerResetTimeoutMs);
        
        logger.info("Threading initialized with {} indexing threads and max {} concurrent tasks.", 
                   indexingThreads, maxConcurrentTasks);
    }

    /**
     * Procesa una entidad dentro de una transacción read-only.
     * Este método encapsula TODO el ciclo de vida de procesamiento de una entidad:
     * 1. Carga la entidad desde la BD dentro de la transacción
     * 2. Accede a los campos lazy (automáticamente cargados por JPA)
     * 3. Genera el documento JSON para Elasticsearch
     * 4. Indexa el documento directamente en Elasticsearch
     * 
     * Todo ocurre en el mismo thread con la misma transacción, garantizando
     * que los campos lazy se cargan correctamente sin LazyInitializationException.
     * 
     * @param entityId UUID de la entidad a procesar
     */
    private void processEntityInTransaction(UUID entityId) throws EntityIndexingException {
        logger.debug("Starting transaction for entity: {}", entityId);
        
        // Crear transacción read-only optimizada
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        
        // OPTIMIZACIÓN: Marcar como read-only para mejor performance
        // - Hibernate: No flush automático, sin dirty checking
        // - PostgreSQL: No genera WAL, no adquiere write locks, usa snapshots optimizados
        // - Spring: Menor overhead en commit/rollback
        // Ganancia esperada: ~20-30% más rápido en indexación masiva
        def.setReadOnly(true);
        
        // Usar READ_COMMITTED para prevenir dirty reads y garantizar lecturas consistentes
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        // REQUIRES_NEW: Cada thread de indexación tiene su propia transacción independiente
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        // Timeout de 30 segundos para detectar problemas
        def.setTimeout(30);
        
        TransactionStatus status = transactionManager.getTransaction(def);
        logger.debug("Read-only transaction started for entity: {}", entityId);
        
        try {
            // 1. Cargar entidad desde la BD (dentro de la transacción)
            Optional<Entity> entityOpt = entityDataService.getEntityById(entityId);
            if (entityOpt.isEmpty()) {
                logger.error("Entity not found: {}", entityId);
                transactionManager.commit(status);
                return;
            }
            
            Entity entity = entityOpt.get();
            logger.debug("Entity loaded: {}", entityId);
            
            // 2. Generar documento Elasticsearch (dentro de transacción - lazy loading funciona)
            String json = generateElasticDocument(entity);
            
            // Obtener el nombre del índice
            EntityType type = entityModelCache.getObjectById(EntityType.class, entity.getEntityTypeId());
            EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(type.getName());
            String indexName = entityIndexingConfig != null ? entityIndexingConfig.getName() : null;
            
            if (indexName == null) {
                logger.warn("No indexing config found for entity type: {} (entity: {})", type.getName(), entityId);
                transactionManager.commit(status);
                return;
            }
            
            // 3. Commit de la transacción read-only (antes de indexar)
            transactionManager.commit(status);
            logger.debug("Read-only transaction committed for entity: {}", entityId);
            documentsProduced.incrementAndGet();
            
            // 4. Indexar directamente en Elasticsearch (fuera de transacción)
            indexDocumentInElasticsearch(entityId.toString(), json, indexName);
            
        } catch (Exception e) {
            transactionManager.rollback(status);
            logger.error("Read-only transaction rolled back for entity {}: {}", entityId, e.getMessage(), e);
            throw new EntityIndexingException("Error processing entity: " + entityId + ". " + e.getMessage());
        }
    }
    
    /**
     * Genera el documento JSON de Elasticsearch para una entidad.
     * Este método procesa la entidad y todas sus relaciones anidadas.
     */
    private String generateElasticDocument(Entity entity) throws EntityIndexingException {
        try {
            // Agregar entidad al cache local del thread
            entityDataService.addEntityToCache(entity);
            logger.debug("Entity added to thread cache: {}", entity.getId());

            EntityType type = entityModelCache.getObjectById(EntityType.class, entity.getEntityTypeId());
            String entityTypeName = type.getName();
            EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(entityTypeName);

            if (entityIndexingConfig == null) {
                throw new EntityIndexingException("No indexing config found for entity type: " + entityTypeName);
            }

            // Crear la entidad elástica
            JSONEntityElastic elasticEntity = createElasticEntity(entityIndexingConfig, entity);

            // Procesar entidades anidadas
            for (EntityIndexingConfig nestedEntityConfig : entityIndexingConfig.getIndexNestedEntities()) {
                String relationName = nestedEntityConfig.getEntityRelation();
                String nestedEntityTypeName = nestedEntityConfig.getEntityType();

                Boolean isFromMember = entityModelCache.isFromRelation(relationName, nestedEntityTypeName);

                Collection<UUID> nestedRelatedEntityIds;
                try {
                    nestedRelatedEntityIds = entityDataService.getMemberRelatedEntitiesIds(entity.getId(), relationName, isFromMember);
                } catch (EntitiyRelationXMLLoadingException e) {
                    logger.warn("Error getting nested entities for relation {}: {}", relationName, e.getMessage());
                    continue;
                }

                for (UUID nestedRelatedEntityId : nestedRelatedEntityIds) {
                    Optional<Entity> nestedEntity = entityDataService.getEntityById(nestedRelatedEntityId);

                    if (nestedEntity.isEmpty()) {
                        logger.warn("Nested entity not found: " + nestedRelatedEntityId);
                        continue;
                    } else {
                        JSONEntityElastic relatedElasticEntity = createElasticEntity(nestedEntityConfig, nestedEntity.get());
                        elasticEntity.addRelatedEntity(nestedEntityConfig.getName(), relatedElasticEntity);
                    }
                }
            }
            
            // Serializar a JSON
            return jsonMapper.writeValueAsString(elasticEntity);
            
        } catch (Exception e) {
            logger.error("Error generating elastic document for entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Error generating elastic document: " + entity.getId() + ". " + e.getMessage());
        }
    }
    
    /**
     * Indexa un documento directamente en Elasticsearch con retry.
     */
    private void indexDocumentInElasticsearch(String entityId, String json, String indexName) {
        // Verificar si el circuit breaker está abierto
        if (circuitBreaker.isOpen()) {
            logger.error("[CIRCUIT BREAKER OPEN] Rejecting document {} to index '{}'. Status: {}", 
                       entityId, indexName, circuitBreaker.getStatus());
            documentsFailedPermanently.incrementAndGet();
            return;
        }
        
        boolean retry = true;
        int retries = 0;
        int millis = 1000;
        
        while (retry && retries < maxRetries) {
            try {
                IndexRequest request = new IndexRequest(indexName)
                    .id(entityId)
                    .source(json, XContentType.JSON);
                    
                elasticClient.index(request, RequestOptions.DEFAULT);
                
                // Éxito - registrar y resetear circuit breaker si estaba con fallos
                documentsIndexed.incrementAndGet();
                circuitBreaker.recordSuccess();
                logger.debug("Document indexed successfully: {}", entityId);
                return;
                
            } catch (IOException e) {
                retries++;
                circuitBreaker.recordFailure();
                
                if (retries >= maxRetries) {
                    logger.error("Failed to index document {} after {} retries: {}", 
                               entityId, maxRetries, e.getMessage());
                    documentsFailedPermanently.incrementAndGet();
                    retry = false;
                } else {
                    logger.warn("Retry {}/{} for document {}: {}", retries, maxRetries, entityId, e.getMessage());
                    try {
                        Thread.sleep(millis);
                        millis *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        retry = false;
                    }
                }
            }
        }
    }

    @Override
    public void prePage() throws EntityIndexingException {
       }

    @Override
    public void index(Entity entity) throws EntityIndexingException {
        if (shutdown) {
            throw new EntityIndexingException("Indexer is shutting down");
        }
        
        // Solo capturar el UUID - el worker cargará la entidad completa en su propia transacción
        final UUID entityId = entity.getId();
        logger.debug("Queueing entity for async processing: {}", entityId);
        
        try {
            // Adquirir permiso del semáforo antes de encolar
            concurrentTasksSemaphore.acquire();
            logger.debug("Acquired semaphore permit for entity: {} (available: {})", 
                        entityId, concurrentTasksSemaphore.availablePermits());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EntityIndexingException("Interrupted while waiting for processing slot for entity: " + entityId);
        }
        
        // Registrar este hilo en el phaser
        activeIndexingPhaser.register();
        logger.debug("Registered with phaser. Current parties: {}", activeIndexingPhaser.getRegisteredParties());
        
        try {
            // Enviar solo el UUID al executor - cada worker manejará todo el proceso
            CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("Worker thread starting for entity: {}", entityId);
                    
                    // El worker carga la entidad, genera JSON e indexa en Elasticsearch
                    // Todo en UNA SOLA transacción read-only garantizando lazy loading
                    processEntityInTransaction(entityId);
                    
                    logger.debug("Worker thread completed for entity: {}", entityId);
                    
                } catch (Exception e) {
                    logger.error("Error in worker thread for entity {}: {}", entityId, e.getMessage(), e);
                } finally {
                    // Liberar semáforo y desregistrar del phaser
                    concurrentTasksSemaphore.release();
                    activeIndexingPhaser.arriveAndDeregister();
                    logger.debug("Released semaphore and deregistered from phaser for entity: {} (available: {})", 
                                entityId, concurrentTasksSemaphore.availablePermits());
                }
            }, indexingExecutor);
            
            // El método retorna inmediatamente - el trabajo se hace en el worker thread
            logger.debug("Entity {} queued for async processing", entityId);
            
        } catch (Exception e) {
            // Si hay error en el setup, liberar semáforo y desregistrar del phaser
            concurrentTasksSemaphore.release();
            activeIndexingPhaser.arriveAndDeregister();
            logger.error("Error setting up async processing for entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Error queueing entity for async processing: " + entity.getId() + ". " + e.getMessage());
        }
    }

    private JSONEntityElastic createElasticEntity(EntityIndexingConfig config, Entity entity) throws EntityIndexingException {
        // create the elastic entity
        JSONEntityElastic jsonEntityElastic = new JSONEntityElastic();
        try {
            // get the entity type
            EntityType entityType = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());
        
            // set the id based on entity uuid
            jsonEntityElastic.setId(entity.getId().toString());

            // set the entity type
            if (config.getindexEntityType())
                jsonEntityElastic.setType(entityType.getName());

            // set the entity semantic ids
            if (config.getIndexSemanticIds()) {
                for (SemanticIdentifier semanticId : entity.getSemanticIdentifiers())
                    jsonEntityElastic.addSemanticId(semanticId.getIdentifier());
            }

            // set the entity fields
            for (FieldIndexingConfig fieldConfig : config.getIndexFields())
                try {
                    processFieldConfig(entity, fieldConfig, jsonEntityElastic);
                } catch (Exception e) {
                    throw new EntityIndexingException("Error processing field: " + fieldConfig.getName() + " :: " + e.getMessage());
                }

        } catch (Exception e) {
            throw new EntityIndexingException("Error creating JSONElasticEntity: " + config.getName() + " :: " + config.getEntityType() + " from entity: " + entity.getId() + " :: " + e.getMessage());
        }
        return jsonEntityElastic;
    }

    private void processFieldConfig(Entity entity, FieldIndexingConfig config, JSONEntityElastic ientity) throws EntityIndexingException {
        if (config.getSourceField() == null)
            throw new EntityIndexingException("Error Indexing Entity Field " + config.getName() + " source field is not defined");

        try {
            if (config.getSourceRelation() != null) { // is a relation indexing
                if (config.getSourceMember() != null) { // is a related entity field
                    // check if the relation is from or to the entity and get the related entities ids
                    Boolean isFromMember = entityModelCache.isFromRelation(config.getSourceRelation(), config.getSourceMember());
                    for (UUID relatedEntityId : entityDataService.getMemberRelatedEntitiesIds(entity.getId(), config.getSourceRelation(), isFromMember)) {
                        Entity relatedEntity = entityDataService.getEntityById(relatedEntityId).get();
                        relatedEntity.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                        processFieldOccurrences(relatedEntity.getFieldOccurrences(config.getSourceField()), config, ientity);
                    }
                } else { // is a relation attribute
                    EntityType entityType = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());
                    Boolean isFromMember = entityModelCache.isFromRelation(config.getSourceRelation(), entityType.getName());

                    for (Relation relation : entityDataService.getRelationsWithThisEntityAsMember(entity.getId(), config.getSourceRelation(), isFromMember)) {
                        relation.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                        processFieldOccurrences(relation.getFieldOccurrences(config.getSourceField()), config, ientity);
                    }
                }
            } else { // is a entity field so process we process the field occurrences of this entity only
                entity.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                processFieldOccurrences(entity.getFieldOccurrences(config.getSourceField()), config, ientity);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new EntityIndexingException("Error processing field: " + config.getSourceField() + " subfield: " + config.getSourceSubfield() + "::" + e.getMessage());
        }
    }

    private void processFieldOccurrences(Collection<FieldOccurrence> occurrences, FieldIndexingConfig config, JSONEntityElastic ientity) {
        // if there are no occurrences, return
        if (occurrences == null || occurrences.size() == 0)
            return;

        // if field filter is defined and the services is available, apply it
        if (fieldOccurrenceFilterService != null && config.getFilter() != null) {
            // get the params from the config
            Map<String, String> filterParams = config.getParams();

            // add the field name to the params
            filterParams.put("field", config.getSourceField());
            filterParams.put("subfield", config.getSourceSubfield());

            // check if preferred flag is set and add it to the params
            if (config.getPreferredValuesOnly())
                filterParams.put("preferred", "true");

            occurrences = fieldOccurrenceFilterService.filter(occurrences, config.getFilter(), filterParams);
        }

        for (FieldOccurrence occr : occurrences)
            try {
                String value;

                if (config.getSourceSubfield() != null)
                    value = occr.getValue(config.getSourceSubfield());
                else
                    value = occr.getValue();

                // add the field occurrence to the json entity
                ientity.addFieldOccurrence(config.getName(), value);

            } catch (EntityRelationException e) {
                logger.error("Error indexing field: " + config.getSourceField() + " subfield: " + config.getSourceSubfield() + "::" + e.getMessage());
            }
    }

    @Override
    public void flush() {
        synchronized (flushLock) {
            logger.info("Starting flush operation...");

            // Wait for all active indexing tasks to finish
            logger.info("Waiting for active indexing threads to complete...");
            int phase = activeIndexingPhaser.arrive();
            try {
                activeIndexingPhaser.awaitAdvanceInterruptibly(phase, 30, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for indexing threads to complete");
                return;
            }
            
            // Reportar estadísticas finales cuando todos los tasks terminan
            ProcessingStats finalStats = getProcessingStats();
            logger.info("All indexing tasks completed successfully.");
            logger.info("Final indexing statistics - Documents produced: {}, indexed: {}, failed: {}",
                       finalStats.getDocumentsProduced(),
                       finalStats.getDocumentsIndexed(), 
                       finalStats.getDocumentsFailed());
            
            if (finalStats.getDocumentsFailed() > 0) {
                logger.warn("Some documents failed permanently during indexing. Failed count: {}", 
                           finalStats.getDocumentsFailed());
            }
            
            logger.info("Flush operation completed successfully.");
        }
    }

    @Override
    public void delete(String entityId) throws EntityIndexingException {
        logger.warn("Delete operation not yet implemented for JSONElasticEntityIndexerThreadedImpl.");
    }

    @Override
    public void deleteAll(Collection<String> idList) throws EntityIndexingException {
        logger.warn("DeleteAll operation not yet implemented for JSONElasticEntityIndexerThreadedImpl.");
    }

    @Override
    public void close() throws IOException {
        synchronized (flushLock) {
            logger.info("Shutting down JSONElasticEntityIndexerThreadedImpl...");
            shutdown = true;

            // 1. Flush any remaining work
            flush();

            // 2. Shutdown indexing executor
            shutdownExecutor(indexingExecutor, "IndexingExecutor");

            // 3. Close Elasticsearch client
            if (elasticClient != null) {
                try {
                    elasticClient.close();
                } catch (IOException e) {
                    logger.error("Error closing Elasticsearch client: " + e.getMessage(), e);
                }
            }

            logger.info("JSONElasticEntityIndexerThreadedImpl shutdown completed.");
        }
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null) {
            logger.info("Shutting down {}", name);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("{} did not terminate gracefully, forcing shutdown", name);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                logger.warn("{} shutdown interrupted", name);
            }
        }
    }

    /**
     * Resetea manualmente el circuit breaker.
     * Útil cuando se sabe que Elasticsearch se ha recuperado y se quiere reintentar.
     */
    public void resetCircuitBreaker() {
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            logger.info("Circuit breaker has been manually reset");
        } else {
            logger.warn("Circuit breaker is not initialized");
        }
    }
    
    /**
     * Obtener estadísticas del procesamiento actual
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
                Math.max(0, activeIndexingPhaser.getRegisteredParties() - 1),
                documentsProduced.get(),
                documentsIndexed.get(),
                documentsFailedPermanently.get(),
                concurrentTasksSemaphore.availablePermits(),
                maxConcurrentTasks,
                circuitBreaker != null ? circuitBreaker.getStatus() : "NOT INITIALIZED"
        );
    }
    
    /**
     * Imprime un reporte detallado del estado actual de indexación.
     * Útil para monitoreo manual o debugging.
     */
    public void logCurrentStatus() {
        ProcessingStats stats = getProcessingStats();
        logger.info("=== INDEXING STATUS REPORT ===");
        logger.info("Active Tasks: {}", stats.getActiveTasks());
        logger.info("Available Slots: {}/{}", stats.getAvailableSlots(), stats.getMaxSlots());
        logger.info("Documents Produced: {}", stats.getDocumentsProduced());
        logger.info("Documents Indexed: {}", stats.getDocumentsIndexed());
        logger.info("Documents Failed: {}", stats.getDocumentsFailed());
        logger.info("Circuit Breaker Status: {}", stats.getCircuitBreakerStatus());
        logger.info("============================");
    }

    /**
     * Clase para estadísticas de procesamiento
     */
    public static class ProcessingStats {
        private final int activeTasks;
        private final long documentsProduced;
        private final long documentsIndexed;
        private final long documentsFailed;
        private final int availableSlots;
        private final int maxSlots;
        private final String circuitBreakerStatus;

        public ProcessingStats(int activeTasks, long documentsProduced, 
                             long documentsIndexed, long documentsFailed,
                             int availableSlots, int maxSlots, String circuitBreakerStatus) {
            this.activeTasks = activeTasks;
            this.documentsProduced = documentsProduced;
            this.documentsIndexed = documentsIndexed;
            this.documentsFailed = documentsFailed;
            this.availableSlots = availableSlots;
            this.maxSlots = maxSlots;
            this.circuitBreakerStatus = circuitBreakerStatus;
        }

        public int getActiveTasks() { return activeTasks; }
        public long getDocumentsProduced() { return documentsProduced; }
        public long getDocumentsIndexed() { return documentsIndexed; }
        public long getDocumentsFailed() { return documentsFailed; }
        public int getAvailableSlots() { return availableSlots; }
        public int getMaxSlots() { return maxSlots; }
        public int getUsedSlots() { return maxSlots - availableSlots; }
        public String getCircuitBreakerStatus() { return circuitBreakerStatus; }
    }

    // --- ELASTIC SPECIFIC METHODS ---

    private void loadOccurFilters() {
        try {
            fieldOccurrenceFilterService = FieldOccurrenceFilterService.getServiceInstance(context);
            if (fieldOccurrenceFilterService != null) {
                fieldOccurrenceFilterService.loadFiltersFromApplicationContext(context);
            }
            logger.debug("fieldOccurrenceFilterService: " + fieldOccurrenceFilterService.getFilters().toString());
        } catch (Exception e) {
            logger.warn("Error loading field occurrence filters: " + e.getMessage());
        }
    }

    private RestHighLevelClient buildElasticRestClient() throws EntityIndexingException {
        RestHighLevelClient localClient = null;

        try {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username.trim(), password.trim()));

            final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();

            RestClientBuilder builder = RestClient.builder(new HttpHost(host.trim(), port, useSSL ? "https" : "http"))
                    .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                            HttpAsyncClientBuilder builder = httpClientBuilder;

                            if (useSSL)
                                builder = httpClientBuilder.setSSLContext(sslContext);
                            else
                                builder = httpClientBuilder;

                            if (authenticate)
                                builder = builder.setDefaultCredentialsProvider(credentialsProvider);

                            return builder;
                        }
                    });

            localClient = new RestHighLevelClient(builder);
            localClient.ping(RequestOptions.DEFAULT);
            logger.info("Elasticsearch/Opensearch client created: " + host + ":" + port + (useSSL ? " using SSL" : " ") + (authenticate ? " using authentication" : ""));

            return localClient;

        } catch (Exception e) {
            logger.error("Error connecting elasticsearch/opensearch:" + host + ":" + port + " :: " + e.getMessage());
            throw new EntityIndexingException("Elastic Client creation error");
        }
    }

    private void createIndexMappings() throws EntityIndexingException {
        try {
            for (EntityIndexingConfig entityIndexingConfig : indexingConfiguration.getEntityIndices()) {
                createOrUpdateIndexMapping(entityIndexingConfig);
            }
        } catch (Exception e) {
            throw new EntityIndexingException("Error when creating index mapping from file" + indexingConfigFilename + " :: " + e.getMessage());
        }
    }

    private Map<String, Object> createTypeMapping(String type) {
        Map<String, Object> fieldMapping = new HashMap<String, Object>();
        fieldMapping.put("type", type);
        return fieldMapping;
    }

    private void addElasticParamsToMapping(Map<String, Object> fieldMapping, Map<String, String> params) {
        params.forEach((key, value) -> {
            if (key.startsWith(ELASTIC_PARAM_PREFIX)) {
                fieldMapping.put(key.replace(ELASTIC_PARAM_PREFIX, ""), value);
            }
        });
    }

    private void createOrUpdateIndexMapping(EntityIndexingConfig entityIndexingConfig) throws EntityIndexingException {
        HashMap<String, Object> typesMapping = new HashMap<String, Object>();
        HashMap<String, Object> mapping = new HashMap<String, Object>();
        mapping.put(MAPPING_PROPERTIES_STR, typesMapping);

        for (FieldIndexingConfig fieldConfig : entityIndexingConfig.getIndexFields()) {
            Map<String, Object> fieldMapping = createTypeMapping(fieldConfig.getType());
            addElasticParamsToMapping(fieldMapping, fieldConfig.getParams());
            typesMapping.put(fieldConfig.getName(), fieldMapping);
        }

        typesMapping.put(ID_FIELD, createTypeMapping(ID_FIELD_TYPE));
        addNestedEntitiesToMapping(entityIndexingConfig.getIndexNestedEntities(), typesMapping);

        try {
            Boolean indexExists = elasticClient.indices().exists(new GetIndexRequest(entityIndexingConfig.getName()), RequestOptions.DEFAULT);

            if (indexExists) {
                logger.warn("Index " + entityIndexingConfig.getName() + " already exists. Is not possible to update mapping !!!");
            } else {
                logger.info("Index " + entityIndexingConfig.getName() + " does not exist, creating it. With mapping: " + mapping.toString() + "");

                CreateIndexRequest createIndexRequest = new CreateIndexRequest(entityIndexingConfig.getName());
                createIndexRequest.mapping(mapping);
                elasticClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);

                logger.info("Index " + entityIndexingConfig.getName() + " created successfully");
            }

        } catch (IOException e) {
            throw new EntityIndexingException("Error trying index creation / mapping creation: " + entityIndexingConfig.getName() + " :: " + e.getMessage());
        }
    }

    private void addNestedEntitiesToMapping(Collection<EntityIndexingConfig> nestedEntityConfigs, HashMap<String, Object> typesMapping) {
        nestedEntityConfigs.forEach(nestedEntityConfig -> {
            HashMap<String, Object> nestedTypesMapping = new HashMap<String, Object>();
            HashMap<String, Object> nestedMapping = new HashMap<String, Object>();
            nestedMapping.put(MAPPING_PROPERTIES_STR, nestedTypesMapping);
            typesMapping.put(nestedEntityConfig.getName(), nestedMapping);

            for (FieldIndexingConfig fieldConfig : nestedEntityConfig.getIndexFields()) {
                Map<String, Object> fieldMapping = createTypeMapping(fieldConfig.getType());
                addElasticParamsToMapping(fieldMapping, fieldConfig.getParams());
                nestedTypesMapping.put(fieldConfig.getName(), fieldMapping);
            }

            nestedTypesMapping.put(ID_FIELD, createTypeMapping(ID_FIELD_TYPE));
        });
    }

    // --- THREADING CLASSES ---

    /**
     * Circuit Breaker para proteger contra fallos en cascada de Elasticsearch.
     * Abre el circuito después de un número configurable de fallos consecutivos,
     * y se resetea automáticamente después de un timeout.
     */
    private class ElasticCircuitBreaker {
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final int maxConsecutiveFailures;
        private volatile boolean open = false;
        private volatile long openedAt = 0;
        private final long resetTimeoutMs;
        
        public ElasticCircuitBreaker(int maxConsecutiveFailures, long resetTimeoutMs) {
            this.maxConsecutiveFailures = maxConsecutiveFailures;
            this.resetTimeoutMs = resetTimeoutMs;
        }
        
        /**
         * Verifica si el circuit breaker está abierto.
         * Si ha pasado el timeout de reset, intenta cerrarlo automáticamente.
         */
        public boolean isOpen() {
            if (open && (System.currentTimeMillis() - openedAt) > resetTimeoutMs) {
                logger.info("[CIRCUIT BREAKER] Attempting to reset after timeout of {}ms", resetTimeoutMs);
                reset();
            }
            return open;
        }
        
        /**
         * Registra una operación exitosa.
         * Resetea el contador de fallos y cierra el circuito si estaba abierto.
         */
        public void recordSuccess() {
            int previousFailures = consecutiveFailures.getAndSet(0);
            if (open) {
                logger.info("[CIRCUIT BREAKER] CLOSED after successful operation (was open with {} failures)", 
                           previousFailures);
                open = false;
            }
        }
        
        /**
         * Registra un fallo en la operación.
         * Abre el circuito si se alcanza el umbral de fallos consecutivos.
         */
        public void recordFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= maxConsecutiveFailures && !open) {
                logger.error("[CIRCUIT BREAKER] OPENED after {} consecutive failures. " +
                           "Elasticsearch operations will be rejected for {}ms", 
                           failures, resetTimeoutMs);
                open = true;
                openedAt = System.currentTimeMillis();
            } else if (!open) {
                logger.warn("[CIRCUIT BREAKER] Failure recorded ({}/{})", failures, maxConsecutiveFailures);
            }
        }
        
        /**
         * Resetea el circuit breaker manualmente.
         */
        public void reset() {
            consecutiveFailures.set(0);
            open = false;
            logger.info("[CIRCUIT BREAKER] Manually reset");
        }
        
        /**
         * Obtiene el estado actual del circuit breaker.
         */
        public String getStatus() {
            if (open) {
                long elapsed = System.currentTimeMillis() - openedAt;
                long remaining = Math.max(0, resetTimeoutMs - elapsed);
                return String.format("OPEN (failures: %d, reset in: %dms)", 
                                    consecutiveFailures.get(), remaining);
            } else {
                return String.format("CLOSED (failures: %d/%d)", 
                                    consecutiveFailures.get(), maxConsecutiveFailures);
            }
        }
    }

}
