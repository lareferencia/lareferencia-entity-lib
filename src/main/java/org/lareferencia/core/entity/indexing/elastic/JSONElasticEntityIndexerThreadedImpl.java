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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
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

import com.fasterxml.jackson.core.JsonProcessingException;
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

    @Value("${elastic.indexer.writer.threads:0}")
    private Integer elasticWriterThreadsConfig; // 0 = auto-calculate

    private IndexingConfiguration indexingConfiguration;
    private String indexingConfigFilename;
    private Map<String, EntityIndexingConfig> configsByEntityType;
    private ObjectMapper jsonMapper;
    private RestHighLevelClient elasticClient = null;
    private FieldOccurrenceFilterService fieldOccurrenceFilterService;

    private static int MAX_RETRIES = 10;

    // --- THREADING COMPONENTS ---
    // Marker objects for queue control
    private static final Object POISON_PILL = new Object();
    private static class FlushMarker {
        final CountDownLatch latch;
        FlushMarker(CountDownLatch latch) {
            this.latch = latch;
        }
    }

    private ExecutorService indexingExecutor; // For producers
    private ExecutorService utilityExecutor; // For monitoring
    
    private BlockingQueue<Object> documentBuffer; // Main input queue for JSONEntityElastic, FlushMarkers, POISON_PILL
    private final List<BlockingQueue<Object>> outputQueues = new ArrayList<>();
    private final List<Thread> writerThreads = new ArrayList<>();
    private Thread distributorThread;
    private final List<Closeable> writers = new ArrayList<>();

    private final Phaser activeIndexingPhaser = new Phaser(1); // Tracks producer tasks
    private volatile boolean shutdown = false;
    private final Object flushLock = new Object();
    
    // Configuração de threading
    private int indexingThreads = Runtime.getRuntime().availableProcessors();
    private int bufferSize = 10000;
    private long monitoringIntervalSeconds = 10;
    
    // Configuração específica para Elasticsearch - múltiples escritores concurrentes
    private int elasticWriterThreads = Math.max(2, indexingThreads / 2); // Al menos 2 escritores
    
    // Limitar tareas concurrentes para evitar saturar la BD
    private int maxConcurrentTasks = indexingThreads * 2;
    private Semaphore concurrentTasksSemaphore;

    // Stats
    private final AtomicLong documentsProduced = new AtomicLong(0);
    private final AtomicLong documentsConsumed = new AtomicLong(0);
    private final AtomicLong documentsIndexed = new AtomicLong(0);
    
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
        this.documentBuffer = new LinkedBlockingQueue<>(bufferSize);
        this.indexingExecutor = Executors.newFixedThreadPool(indexingThreads);
        this.utilityExecutor = Executors.newSingleThreadExecutor();
        
        // Calculate optimal number of Elasticsearch writers
        if (elasticWriterThreadsConfig != null && elasticWriterThreadsConfig > 0) {
            this.elasticWriterThreads = elasticWriterThreadsConfig;
        } else {
            // Auto-calculate: use more writers for better Elasticsearch concurrency
            this.elasticWriterThreads = Math.max(2, Math.min(indexingThreads, 6)); // Entre 2 y 6 escritores
        }
        
        // Inicializar semáforo para limitar tareas concurrentes
        this.concurrentTasksSemaphore = new Semaphore(maxConcurrentTasks);

        // Create multiple Elasticsearch writers for concurrent indexing
        for (int i = 0; i < elasticWriterThreads; i++) {
            BlockingQueue<Object> queue = new LinkedBlockingQueue<>(bufferSize);
            outputQueues.add(queue);
            
            try {
                ElasticWriter writer = new ElasticWriter(queue, i);
                writers.add(writer);
                Thread writerThread = new Thread(writer, "ElasticWriter-Thread-" + i);
                writerThreads.add(writerThread);
            } catch (Exception e) {
                logger.error("Error creating Elastic writer {}: {}", i, e.getMessage(), e);
                throw new RuntimeException("Failed to create Elastic writer " + i, e);
            }
        }

        // Create and start the distributor thread
        this.distributorThread = new Thread(new Distributor(), "DocumentDistributor-Thread");
        this.distributorThread.start();

        // Start all writer threads
        for (Thread t : writerThreads) {
            t.start();
        }
        
        // Iniciar el monitor de estado
        startMonitoringThread();
        
        logger.info("Threading initialized with {} indexing threads, {} Elasticsearch writers, max {} concurrent tasks, and 1 distributor.", 
                   indexingThreads, elasticWriterThreads, maxConcurrentTasks);
    }

    private void startMonitoringThread() {
        utilityExecutor.submit(() -> {
            logger.info("Monitoring thread started.");
            while (!shutdown) {
                try {
                    Thread.sleep(monitoringIntervalSeconds * 1000);
                    
                    ProcessingStats stats = getProcessingStats();
                    logger.info("[STATUS] Buffer: {}/{} ({:.2f}%). Active Tasks: {}. Slots: {}/{}. " +
                               "Documents: P:{}, C:{}, I:{}.",
                               stats.getBufferSize(), stats.getBufferCapacity(), stats.getBufferUsagePercentage(),
                               stats.getActiveTasks(), stats.getUsedSlots(), stats.getMaxSlots(), 
                               stats.getDocumentsProduced(), stats.getDocumentsConsumed(), stats.getDocumentsIndexed());
                               
                    // Alertar se o buffer está muito cheio
                    if (stats.getBufferUsagePercentage() > 80.0) {
                        logger.warn("Buffer usage is high: {:.2f}%", stats.getBufferUsagePercentage());
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in monitoring thread: " + e.getMessage(), e);
                }
            }
            logger.info("Monitoring thread finished.");
        });
    }

    private void processEntityWithTransaction(Entity entity) throws EntityIndexingException {
        logger.debug("Processing entity with transaction: {}", entity.getId());
        
        // Crear una nueva transacción
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        TransactionStatus status = transactionManager.getTransaction(def);
        logger.debug("Transaction started for entity: {}", entity.getId());
        
        try {
            processEntityInternal(entity);
            transactionManager.commit(status);
            logger.debug("Transaction committed for entity: {}", entity.getId());
        } catch (Exception e) {
            transactionManager.rollback(status);
            logger.error("Transaction rolled back for entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Error processing entity: " + entity.getId() + ". " + e.getMessage());
        }
    }

    @Override
    public void prePage() throws EntityIndexingException {
        // En esta implementación, no necesitamos transacciones por página
        // ya que los threads manejan la concurrencia
    }

    @Override
    public void index(Entity entity) throws EntityIndexingException {
        if (shutdown) {
            throw new EntityIndexingException("Indexer is shutting down");
        }
        
        logger.debug("Starting async index process for entity: {} (type: {})", entity.getId(), entity.getEntityTypeId());
        
        try {
            // Adquirir permiso del semáforo antes de procesar
            concurrentTasksSemaphore.acquire();
            logger.debug("Acquired semaphore permit for entity: {} (available: {})", 
                        entity.getId(), concurrentTasksSemaphore.availablePermits());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EntityIndexingException("Interrupted while waiting for processing slot for entity: " + entity.getId());
        }
        
        // Registrar este hilo en el phaser
        activeIndexingPhaser.register();
        logger.debug("Registered with phaser. Current parties: {}", activeIndexingPhaser.getRegisteredParties());
        
        try {
            // Capturar solo el UUID de la entidad para evitar problemas de concurrencia con Hibernate
            final UUID entityId = entity.getId();
            
            // Enviar solo los IDs al procesamiento paralelo
            CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("Starting parallel processing for entity: {}", entityId);
                    
                    // Recargar la entidad con una nueva sesión/transacción
                    Optional<Entity> reloadedEntity = entityDataService.getEntityById(entityId);
                    if (reloadedEntity.isEmpty()) {
                        logger.error("Entity not found after reload: {}", entityId);
                        return;
                    }
                    
                    // Pre-cargar datos para evitar lazy loading
                    Entity preloadedEntity = preloadEntityData(reloadedEntity.get());
                    
                    // Procesar la entidad en una transacción separada
                    processEntityWithTransaction(preloadedEntity);
                    
                    logger.debug("Parallel processing completed for entity: {}", entityId);
                    
                } catch (Exception e) {
                    logger.error("Error in parallel processing for entity {}: {}", entityId, e.getMessage(), e);
                } finally {
                    // Liberar semáforo y desregistrar del phaser
                    concurrentTasksSemaphore.release();
                    activeIndexingPhaser.arriveAndDeregister();
                    logger.debug("Released semaphore and deregistered from phaser for entity: {} (available: {})", 
                                entityId, concurrentTasksSemaphore.availablePermits());
                }
            }, indexingExecutor);
            
            // El método retorna inmediatamente sin esperar el preload ni el procesamiento
            logger.debug("Entity {} queued for async processing (will be reloaded in parallel)", entityId);
            
        } catch (Exception e) {
            // Si hay error en el setup, liberar semáforo y desregistrar del phaser
            concurrentTasksSemaphore.release();
            activeIndexingPhaser.arriveAndDeregister();
            logger.error("Error setting up async processing for entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Error queueing entity for async processing: " + entity.getId() + ". " + e.getMessage());
        }
    }

    /**
     * Pre-carga todas las relaciones y ocurrencias necesarias para evitar lazy loading en threads separados
     */
    private Entity preloadEntityData(Entity entity) throws EntityIndexingException {
        logger.debug("Starting preload for entity: {}", entity.getId());
        
        try {
            // Cargar ocurrencias de la entidad
            EntityType type = entityModelCache.getObjectById(EntityType.class, entity.getEntityTypeId());
            String entityTypeName = type.getName();
            logger.debug("Entity type: {} for entity: {}", entityTypeName, entity.getId());
            
            EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(entityTypeName);

            if (entityIndexingConfig == null) {
                logger.warn("No indexing config found for entity type: {} (entity: {})", entityTypeName, entity.getId());
                return entity;
            }

            // Cargar ocurrencias de atributos si es necesario
            List<FieldIndexingConfig> indexFields = entityIndexingConfig.getIndexFields();
            if (indexFields != null && !indexFields.isEmpty()) {
                entity.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                logger.debug("Loaded field occurrences for entity: {}", entity.getId());
            }

            // Pre-cargar todas las relaciones y sus ocurrencias para entidades anidadas
            int relationCount = 0;
            for (EntityIndexingConfig nestedEntityConfig : entityIndexingConfig.getIndexNestedEntities()) {
                String relationName = nestedEntityConfig.getEntityRelation();
                String nestedEntityTypeName = nestedEntityConfig.getEntityType();

                Boolean isFromMember = entityModelCache.isFromRelation(relationName, nestedEntityTypeName);
                
                try {
                    Collection<UUID> nestedRelatedEntityIds = entityDataService.getMemberRelatedEntitiesIds(entity.getId(), relationName, isFromMember);
                    
                    for (UUID nestedRelatedEntityId : nestedRelatedEntityIds) {
                        Optional<Entity> nestedEntity = entityDataService.getEntityById(nestedRelatedEntityId);
                        if (nestedEntity.isPresent()) {
                            // Pre-cargar ocurrencias de la entidad anidada
                            nestedEntity.get().loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                            relationCount++;
                        }
                    }
                } catch (EntitiyRelationXMLLoadingException e) {
                    logger.warn("Error loading nested entities for relation {}: {}", relationName, e.getMessage());
                }
            }
            
            logger.debug("Preload completed for entity: {}. Total nested entities processed: {}", entity.getId(), relationCount);
            return entity;
            
        } catch (Exception e) {
            logger.error("Error preloading entity data for {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Error preloading entity data: " + entity.getId() + ". " + e.getMessage());
        }
    }

    private void processEntityInternal(Entity entity) throws EntityIndexingException {
        logger.debug("Starting internal processing for entity: {}", entity.getId());
        
        try {
            // Agregar entidad al cache local del thread
            entityDataService.addEntityToCache(entity);
            logger.debug("Entity added to thread cache: {}", entity.getId());

            EntityType type = entityModelCache.getObjectById(EntityType.class, entity.getEntityTypeId());
            String entityTypeName = type.getName();
            EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(entityTypeName);

            if (entityIndexingConfig == null) {
                logger.warn("No indexing config found for entity type: {} (entity: {})", entityTypeName, entity.getId());
                return;
            }

            // Crear la entidad elástica
            JSONEntityElastic elasticEntity = createElasticEntity(entityIndexingConfig, entity);

            // Procesar entidades anidadas (ya pre-cargadas)
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

            // Crear documento de indexación
            ElasticDocument document = new ElasticDocument(elasticEntity, entityIndexingConfig.getName());
            
            // Enviar documento al buffer
            try {
                documentBuffer.put(document);
                documentsProduced.incrementAndGet();
                logger.debug("Document queued for indexing: {}", entity.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EntityIndexingException("Interrupted while queueing document for entity: " + entity.getId());
            }
            
            logger.debug("Internal processing completed for entity: {}", entity.getId());
            
        } catch (Exception e) {
            logger.error("Unexpected error processing entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Unexpected error indexing entity: " + entity.getId() + ". " + e.getMessage());
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

            // 1. Wait for all active indexing tasks (producers) to finish.
            logger.info("Waiting for active indexing threads to complete...");
            int phase = activeIndexingPhaser.arrive();
            try {
                activeIndexingPhaser.awaitAdvanceInterruptibly(phase, 30, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for indexing threads to complete");
                return;
            }
            logger.info("All indexing threads have completed.");

            // 2. Send a flush marker and wait for all writers to process it.
            CountDownLatch flushLatch = new CountDownLatch(writerThreads.size());
            try {
                documentBuffer.put(new FlushMarker(flushLatch));
                logger.info("Flush marker sent, waiting for writers to complete...");
                flushLatch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for writers to flush");
                return;
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

            // 2. Send poison pills to all output queues
            for (BlockingQueue<Object> queue : outputQueues) {
                try {
                    queue.put(POISON_PILL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while sending poison pill");
                }
            }

            // 3. Wait for all writer threads to finish
            for (Thread t : writerThreads) {
                try {
                    t.join(30000); // 30 second timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for writer thread to finish");
                }
            }

            // 4. Wait for distributor thread to finish
            if (distributorThread != null) {
                try {
                    distributorThread.join(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for distributor thread to finish");
                }
            }

            // 5. Close all writers
            for (Closeable writer : writers) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.error("Error closing writer: " + e.getMessage(), e);
                }
            }

            // 6. Shutdown executors
            shutdownExecutor(indexingExecutor, "IndexingExecutor");
            shutdownExecutor(utilityExecutor, "UtilityExecutor");

            // 7. Close Elasticsearch client
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
     * Obtener estadísticas del procesamiento actual
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
                documentBuffer != null ? documentBuffer.size() : 0,
                bufferSize,
                Math.max(0, activeIndexingPhaser.getRegisteredParties() - 1),
                documentsProduced.get(),
                documentsConsumed.get(),
                documentsIndexed.get(),
                concurrentTasksSemaphore.availablePermits(),
                maxConcurrentTasks
        );
    }

    /**
     * Clase para estadísticas de procesamiento
     */
    public static class ProcessingStats {
        private final int bufferSize;
        private final int bufferCapacity;
        private final int activeTasks;
        private final long documentsProduced;
        private final long documentsConsumed;
        private final long documentsIndexed;
        private final int availableSlots;
        private final int maxSlots;

        public ProcessingStats(int bufferSize, int bufferCapacity, int activeTasks, long documentsProduced, 
                             long documentsConsumed, long documentsIndexed, int availableSlots, int maxSlots) {
            this.bufferSize = bufferSize;
            this.bufferCapacity = bufferCapacity;
            this.activeTasks = activeTasks;
            this.documentsProduced = documentsProduced;
            this.documentsConsumed = documentsConsumed;
            this.documentsIndexed = documentsIndexed;
            this.availableSlots = availableSlots;
            this.maxSlots = maxSlots;
        }

        public int getBufferSize() { return bufferSize; }
        public int getBufferCapacity() { return bufferCapacity; }
        public int getActiveTasks() { return activeTasks; }
        public long getDocumentsProduced() { return documentsProduced; }
        public long getDocumentsConsumed() { return documentsConsumed; }
        public long getDocumentsIndexed() { return documentsIndexed; }
        public int getAvailableSlots() { return availableSlots; }
        public int getMaxSlots() { return maxSlots; }
        public int getUsedSlots() { return maxSlots - availableSlots; }
        public double getBufferUsagePercentage() { 
            return bufferCapacity > 0 ? (bufferSize * 100.0) / bufferCapacity : 0.0; 
        }
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
     * Document wrapper para threading
     */
    private static class ElasticDocument {
        private final JSONEntityElastic entity;
        private final String indexName;

        public ElasticDocument(JSONEntityElastic entity, String indexName) {
            this.entity = entity;
            this.indexName = indexName;
        }

        public JSONEntityElastic getEntity() { return entity; }
        public String getIndexName() { return indexName; }
    }

    /**
     * The Distributor thread takes items from the main buffer and distributes them
     * to output queues using intelligent load balancing for optimal Elasticsearch performance.
     */
    private class Distributor implements Runnable {
        private int currentQueueIndex = 0;
        private final Map<String, Integer> indexToWriterMapping = new HashMap<>();
        
        @Override
        public void run() {
            logger.info("Distributor thread started with {} Elasticsearch writers.", outputQueues.size());
            try {
                while (true) {
                    Object item = documentBuffer.take();
                    documentsConsumed.incrementAndGet();

                    if (item == POISON_PILL) {
                        logger.info("Distributor received poison pill, shutting down.");
                        break;
                    }

                    if (item instanceof FlushMarker) {
                        // FlushMarkers need to go to all queues
                        for (BlockingQueue<Object> queue : outputQueues) {
                            queue.put(item);
                        }
                    } else if (item instanceof ElasticDocument) {
                        // Use index-aware distribution for better performance
                        ElasticDocument doc = (ElasticDocument) item;
                        int targetWriter = getTargetWriter(doc.getIndexName());
                        BlockingQueue<Object> selectedQueue = outputQueues.get(targetWriter);
                        selectedQueue.put(item);
                    } else {
                        // Fallback to round-robin for other items
                        BlockingQueue<Object> selectedQueue = outputQueues.get(currentQueueIndex);
                        selectedQueue.put(item);
                        currentQueueIndex = (currentQueueIndex + 1) % outputQueues.size();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Distributor thread interrupted.");
            } catch (Exception e) {
                logger.error("Error in distributor thread: " + e.getMessage(), e);
            } finally {
                logger.info("Distributor thread finished.");
            }
        }
        
        /**
         * Get target writer for a specific index, ensuring documents for the same index
         * tend to go to the same writer for better bulk operation efficiency.
         */
        private int getTargetWriter(String indexName) {
            return indexToWriterMapping.computeIfAbsent(indexName, 
                k -> Math.abs(k.hashCode()) % outputQueues.size());
        }
    }

    /**
     * Abstract base class for writer threads.
     */
    private abstract class AbstractWriter implements Runnable, Closeable {
        protected final BlockingQueue<Object> queue;
        protected final List<ElasticDocument> localBuffer;
        protected final int batchSize = 100; // Tamaño de lote para Elasticsearch
        protected final long maxWaitTimeMs = 5000; // Máximo tiempo de espera para escribir

        protected AbstractWriter(BlockingQueue<Object> queue) {
            this.queue = queue;
            this.localBuffer = new ArrayList<>(batchSize);
        }

        @Override
        public void run() {
            logger.info("Writer thread started: " + Thread.currentThread().getName());
            try {
                long lastWriteTime = System.currentTimeMillis();
                
                while (true) {
                    Object item = queue.poll(1000, TimeUnit.MILLISECONDS);
                    
                    if (item == POISON_PILL) {
                        logger.info("Writer received poison pill, flushing and shutting down.");
                        writeBatch();
                        break;
                    }
                    
                    if (item instanceof FlushMarker) {
                        logger.debug("Writer received flush marker, flushing batch.");
                        writeBatch();
                        ((FlushMarker) item).latch.countDown();
                        continue;
                    }
                    
                    if (item instanceof ElasticDocument) {
                        localBuffer.add((ElasticDocument) item);
                    }
                    
                    // Write batch if buffer is full or timeout reached
                    long currentTime = System.currentTimeMillis();
                    if (localBuffer.size() >= batchSize || 
                        (localBuffer.size() > 0 && (currentTime - lastWriteTime) > maxWaitTimeMs)) {
                        writeBatch();
                        lastWriteTime = currentTime;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Writer thread interrupted: " + Thread.currentThread().getName());
            } catch (Exception e) {
                logger.error("Error in writer thread: " + e.getMessage(), e);
            } finally {
                logger.info("Writer thread finished: " + Thread.currentThread().getName());
            }
        }

        private void writeBatch() {
            if (localBuffer.isEmpty()) return;
            
            try {
                performWrite(new ArrayList<>(localBuffer));
                documentsIndexed.addAndGet(localBuffer.size());
                localBuffer.clear();
            } catch (Exception e) {
                logger.error("Error writing batch: " + e.getMessage(), e);
            }
        }

        protected abstract void performWrite(List<ElasticDocument> batch);
    }

    /**
     * Writer for Elasticsearch with concurrent indexing support.
     */
    private class ElasticWriter extends AbstractWriter {
        private final int writerId;
        
        ElasticWriter(BlockingQueue<Object> queue, int writerId) {
            super(queue);
            this.writerId = writerId;
        }

        @Override
        protected void performWrite(List<ElasticDocument> batch) {
            if (batch.isEmpty()) return;
            
            // Group documents by index for more efficient bulk operations
            Map<String, List<ElasticDocument>> documentsByIndex = new HashMap<>();
            for (ElasticDocument doc : batch) {
                documentsByIndex.computeIfAbsent(doc.getIndexName(), k -> new ArrayList<>()).add(doc);
            }
            
            // Process each index separately for better performance
            for (Map.Entry<String, List<ElasticDocument>> entry : documentsByIndex.entrySet()) {
                String indexName = entry.getKey();
                List<ElasticDocument> indexDocuments = entry.getValue();
                
                BulkRequest bulkRequest = new BulkRequest();
                bulkRequest.timeout("5m");
                
                for (ElasticDocument doc : indexDocuments) {
                    try {
                        IndexRequest indexRequest = new IndexRequest(indexName);
                        indexRequest.id(doc.getEntity().getId());
                        indexRequest.source(jsonMapper.writeValueAsString(doc.getEntity()), XContentType.JSON);
                        bulkRequest.add(indexRequest);
                    } catch (JsonProcessingException e) {
                        logger.error("Writer-{}: Error serializing document to JSON: {}", writerId, e.getMessage(), e);
                    }
                }
                
                if (bulkRequest.numberOfActions() == 0) continue;
                
                // Execute bulk request with retry logic
                executeBulkWithRetry(bulkRequest, indexName, indexDocuments.size());
            }
        }
        
        private void executeBulkWithRetry(BulkRequest bulkRequest, String indexName, int documentCount) {
            Boolean retry = true;
            int retries = 0;
            int millis = 1000; // Start with shorter delay for better responsiveness
            
            while (retry) {
                try {
                    BulkResponse bulkResponse = elasticClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    logger.debug("Writer-{}: Bulk request to index '{}' completed: {} ({} documents)", 
                               writerId, indexName, bulkResponse.status().toString(), documentCount);
                    
                    retry = false;
                    
                    if (bulkResponse.hasFailures()) {
                        logger.warn("Writer-{}: Bulk request to index '{}' has failures: {}", 
                                  writerId, indexName, bulkResponse.buildFailureMessage());
                    }
                    
                } catch (Exception e) {
                    logger.warn("Writer-{}: Retrying bulk request to index '{}': {} -- Warning: {} {}", 
                              writerId, indexName, retries, e.getClass().getSimpleName(), e.getMessage());
                    
                    try { 
                        Thread.sleep(millis); 
                    } catch (InterruptedException se) {
                        Thread.currentThread().interrupt();
                        logger.warn("Writer-{}: Interrupted during retry delay", writerId);
                        break;
                    }
                    
                    retries++;
                    millis = Math.min(millis * 2, 10000); // Cap maximum delay at 10 seconds
                    
                    if (retries > MAX_RETRIES) {
                        logger.error("Writer-{}: Bulk request to index '{}' failed after {} retries: {} {}", 
                                   writerId, indexName, MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage());
                        retry = false;
                    }
                }
            }
        }

        @Override
        public void close() {
            logger.info("Writer-{}: Elasticsearch writer closed", writerId);
        }
    }
}
