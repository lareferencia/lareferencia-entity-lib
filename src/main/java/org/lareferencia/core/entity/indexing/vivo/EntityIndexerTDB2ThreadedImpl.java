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

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.tdb2.TDB2Factory;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class EntityIndexerTDB2ThreadedImpl implements IEntityIndexer, Closeable {

    @Autowired
    EntityDataService entityDataService;

    @Autowired
    EntityModelCache entityModelCache;

    @Autowired
    ApplicationContext context;

    @Autowired
    private PlatformTransactionManager transactionManager;

    FieldOccurrenceFilterService fieldOccurrenceFilterService;

    private static final String ENTITY_ID = "UUID";
    private static final String ATTR_VALUE = "$value";
    private static final String RELATION = "relation";
    private static final String TARGET_ENTITY = "target";
    private static final String NEW_ENTITY = "new";
    private static final String OBJECT_PROPERTY = "objectProperty";

    protected static Logger logger = LogManager.getLogger(EntityIndexerTDB2ThreadedImpl.class);

    protected Map<String, EntityIndexingConfig> configsByEntityType;
    protected Map<String, String> namespaces;
    protected IndexingConfiguration indexingConfig;
    
    protected Dataset dataset;
    protected String graph;
    protected boolean hasTriplestore = false;

    // Modelo compartido para mantener identidad de recursos y mejorar deduplicación
    private Model sharedResourceModel;
    private final Object sharedModelLock = new Object();

    // --- NEW THREADING COMPONENTS ---
    // Marker objects for queue control
    private static final Object POISON_PILL = new Object();
    private static class FlushMarker {
        final CountDownLatch latch;
        FlushMarker(CountDownLatch latch) { this.latch = latch; }
    }

    private ExecutorService indexingExecutor; // For producers
    private ExecutorService utilityExecutor; // For monitoring
    
    private BlockingQueue<Object> tripleBuffer; // Main input queue for Statements, FlushMarkers, POISON_PILL
    private final List<BlockingQueue<Object>> outputQueues = new ArrayList<>();
    private final List<Thread> writerThreads = new ArrayList<>();
    private Thread distributorThread;
    private final List<Closeable> writers = new ArrayList<>(); // To close them gracefully

    private final Phaser activeIndexingPhaser = new Phaser(1); // Tracks producer tasks
    private volatile boolean shutdown = false;
    private final Object flushLock = new Object();
    
    // Configuración de threading
    private int indexingThreads = Runtime.getRuntime().availableProcessors();
    private int bufferSize = 10000;
    private long monitoringIntervalSeconds = 10;
    
    // Limitar tareas concurrentes para evitar saturar la BD
    private int maxConcurrentTasks = indexingThreads * 2; // 2x los threads disponibles
    private Semaphore concurrentTasksSemaphore;

    // Stats
    private final AtomicLong triplesProduced = new AtomicLong(0);
    private final AtomicLong triplesConsumed = new AtomicLong(0); // Now represents triples consumed by distributor
    private final AtomicLong triplesWritten = new AtomicLong(0); // Total triples written by all writers
    
    
    public EntityIndexerTDB2ThreadedImpl() {
        // Constructor
    }

    @Override
    public void setConfig(String configFilePath) {
        try {
            // NO llamar a JenaSystem.init() para evitar que se carguen módulos de TDB1
            // La inicialización se hará automáticamente cuando sea necesaria
            
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

            // Configurar triplestore (opcional)
            List<OutputConfig> tdbOutputs = getOutputsByType(outputs, "triplestore");
            if (!tdbOutputs.isEmpty()) {
                OutputConfig tdbConfig = tdbOutputs.get(0);
                hasTriplestore = true;
                this.graph = tdbConfig.getGraph();
                String directory = tdbConfig.getPath();
                boolean reset = Boolean.parseBoolean(tdbConfig.getReset());
                
                // Configurar deduplicación por defecto - deshabilitada para TDB2
                logger.info("Deduplication disabled for TDB2 (triplestore handles duplicates automatically)");

                try {
                    // Crear directorio si no existe
                    java.io.File tdbDir = new java.io.File(directory);
                    if (!tdbDir.exists()) {
                        tdbDir.mkdirs();
                        logger.info("Created TDB2 directory: {}", directory);
                    }
                    
                    // Configurar propiedades del sistema para mejorar estabilidad de TDB2
                    System.setProperty("tdb:filemode", "mapped"); // Usar memoria mapeada
                    System.setProperty("tdb:synclocation", "true"); // Sincronización más frecuente
                    
                    dataset = TDB2Factory.connectDataset(directory);
                    logger.info("Using TDB2 triplestore at " + directory + (this.graph != null && !this.graph.isEmpty() ? " with graph <" + this.graph + ">" : " with default graph"));

                    if (reset) {
                        clearTDBStore();
                    }
                } catch (Exception e) {
                    logger.error("Failed to initialize TDB2 dataset at {}: {}", directory, e.getMessage(), e);
                    hasTriplestore = false;
                    throw new RuntimeException("TDB2 initialization failed", e);
                }
            } else {
                hasTriplestore = false;
                logger.info("No triplestore configured. Using in-memory models for RDF generation.");
            }

            // Inicializar threading
            initializeThreading();
            
            // Inicializar modelo compartido para recursos
            initializeSharedResourceModel();

        } catch (Exception e) {
            logger.error("Error setting RDF Mapping Config File: " + configFilePath + ". " + e.getMessage(), e);
            throw new RuntimeException("Configuration failed for EntityIndexerTDB2ThreadedImpl", e);
        }
    }
    
    private void initializeThreading() {
        this.tripleBuffer = new LinkedBlockingQueue<>(bufferSize);
        this.indexingExecutor = Executors.newFixedThreadPool(indexingThreads);
        this.utilityExecutor = Executors.newSingleThreadExecutor(); // For monitoring
        
        // Reducir concurrencia cuando se usa TDB2 para evitar problemas de corrupción
        if (hasTriplestore) {
            this.maxConcurrentTasks = Math.max(2, indexingThreads); // Más conservador para TDB2
            logger.info("Using conservative threading settings for TDB2: max {} concurrent tasks", maxConcurrentTasks);
        }
        
        // Inicializar semáforo para limitar tareas concurrentes
        this.concurrentTasksSemaphore = new Semaphore(maxConcurrentTasks);

        List<OutputConfig> outputs = indexingConfig.getOutputs();

        // Create writers and their queues
        for (OutputConfig output : outputs) {
            BlockingQueue<Object> queue = new LinkedBlockingQueue<>(bufferSize);
            outputQueues.add(queue);
            
            try {
                String outputType = output.getType();
                if ("TDB2".equalsIgnoreCase(outputType) || "triplestore".equalsIgnoreCase(outputType)) {
                    if (hasTriplestore) {
                        TDBWriter writer = new TDBWriter(queue, dataset, graph);
                        writers.add(writer);
                        writerThreads.add(new Thread(writer, "TDBWriter-Thread"));
                    }
                } else if ("XML".equalsIgnoreCase(outputType) || "file".equalsIgnoreCase(outputType)) {
                    XMLWriter writer = new XMLWriter(queue, output, namespaces);
                    writers.add(writer);
                    writerThreads.add(new Thread(writer, "XMLWriter-Thread-" + output.getName()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize writer for output: " + output.getName(), e);
            }
        }

        // Create and start the distributor thread
        this.distributorThread = new Thread(new Distributor(), "TripleDistributor-Thread");
        this.distributorThread.start();

        // Start all writer threads
        for (Thread t : writerThreads) {
            t.start();
        }
        
        // Iniciar el monitor de estado
        startMonitoringThread();
        
        logger.info("Threading initialized with {} indexing threads, max {} concurrent tasks, 1 distributor, and {} writers.", 
                   indexingThreads, maxConcurrentTasks, writerThreads.size());
    }
    
    private void startMonitoringThread() {
        utilityExecutor.submit(() -> {
            logger.info("Monitoring thread started.");
            while (!shutdown) {
                try {
                    TimeUnit.SECONDS.sleep(monitoringIntervalSeconds);
                    if (!shutdown) { // Re-check after sleep
                        ProcessingStats stats = getProcessingStats();
                        TDB2Stats tdbStats = getTDB2Stats();
                        logger.info("[STATUS] Buffer: {}/{} ({}%). Active Tasks: {}. Slots: {}/{}. Triples: P:{}, C:{}, W:{}. TDB2: {}",
                                stats.getBufferSize(),
                                stats.getBufferCapacity(),
                                String.format("%.2f", stats.getBufferUsagePercentage()),
                                stats.getActiveTasks(),
                                stats.getUsedSlots(),
                                stats.getMaxSlots(),
                                stats.getTriplesProduced(),
                                stats.getTriplesConsumed(),
                                stats.getTriplesWritten(),
                                tdbStats.toString()
                        );
                        
                        // Log shared model statistics without cleanup
                        synchronized (sharedModelLock) {
                            if (sharedResourceModel != null) {
                                logger.debug("Shared resource model size: {} statements (preserved for deduplication)",
                                           sharedResourceModel.size());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    logger.debug("Monitoring thread interrupted.");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            logger.info("Monitoring thread finished.");
        });
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
            // Adquirir permiso del semáforo antes de procesar (bloqueante si no hay permisos)
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
                    logger.debug("Processing entity {} in thread: {}", entityId, Thread.currentThread().getName());
                    
                    // Crear una nueva transacción COMPLETAMENTE INDEPENDIENTE
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    def.setTimeout(300); // 5 minutos timeout
                    
                    TransactionStatus status = transactionManager.getTransaction(def);
                    logger.debug("Independent transaction started for entity: {}", entityId);
                    
                    try {
                        // Recargar la entidad desde la BD en este thread independiente
                        Optional<Entity> freshEntityOpt = entityDataService.getEntityById(entityId);
                        if (!freshEntityOpt.isPresent()) {
                            logger.warn("Entity {} not found during parallel processing", entityId);
                            return;
                        }
                        
                        Entity freshEntity = freshEntityOpt.get();
                        
                        // MOVER el preload aquí dentro del thread paralelo
                        Entity fullyLoadedEntity = preloadEntityData(freshEntity);
                        logger.debug("Entity data preloaded for entity: {} in parallel thread", entityId);
                        
                        processEntityInternal(fullyLoadedEntity);
                        transactionManager.commit(status);
                        logger.debug("Independent transaction committed for entity: {}", entityId);
                        
                    } catch (Exception e) {
                        logger.error("Error in parallel processing for entity {}: {}", entityId, e.getMessage(), e);
                        try {
                            if (status != null && !status.isCompleted()) {
                                transactionManager.rollback(status);
                                logger.debug("Independent transaction rolled back for entity: {}", entityId);
                            }
                        } catch (Exception rollbackException) {
                            logger.error("Error during rollback for entity {}: {}", entityId, rollbackException.getMessage());
                        }
                        throw new RuntimeException(e);
                    }
                } catch (Exception e) {
                    logger.error("Fatal error in parallel thread for entity {}: {}", entityId, e.getMessage(), e);
                } finally {
                    // Liberar el permiso del semáforo SIEMPRE
                    concurrentTasksSemaphore.release();
                    logger.debug("Released semaphore permit for entity: {} (available: {})", 
                                entityId, concurrentTasksSemaphore.availablePermits());
                    
                    // Desregistrar este hilo del phaser
                    activeIndexingPhaser.arriveAndDeregister();
                    logger.debug("Deregistered from phaser. Current parties: {}", activeIndexingPhaser.getRegisteredParties());
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
                throw new EntityIndexingException("Error mapping entity: " + entity.getId() +
                        " RDF mapping config for " + entityTypeName + " EntityType not found");
            }

            // Cargar ocurrencias de atributos si es necesario
            List<AttributeIndexingConfig> sourceAttributes = entityIndexingConfig.getSourceAttributes();
            if (sourceAttributes != null && !sourceAttributes.isEmpty()) {
                entity.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                logger.debug("Loaded {} attribute configs for entity: {}", sourceAttributes.size(), entity.getId());
            }

            // Pre-cargar todas las relaciones y sus ocurrencias
            int relationCount = 0;
            for (RelationIndexingConfig relationConfig : entityIndexingConfig.getSourceRelations()) {
                String relationName = relationConfig.getName();
                Boolean isFromMember = entityModelCache.isFromRelation(relationName, entityTypeName);
                logger.debug("Processing relation {} (isFromMember: {}) for entity: {}", relationName, isFromMember, entity.getId());

                Set<Relation> relations = entityDataService.getRelationsWithThisEntityAsMember(entity.getId(), relationName, isFromMember);
                logger.debug("Found {} relations of type {} for entity: {}", relations.size(), relationName, entity.getId());
                
                for (Relation relation : relations) {
                    // Forzar carga de entidade relacionada
                    Entity relatedEntity = relation.getRelatedEntity(entity.getId());
                    if (relatedEntity != null) {
                        // Asegurar que la entidad relacionada esté completamente cargada
                        relatedEntity.getId(); // Acceso para forzar carga
                        logger.debug("Preloaded related entity: {} for relation: {}", relatedEntity.getId(), relation.getId());
                    }
                    
                    // Cargar ocurrencias de la relación si es necesario
                    List<AttributeIndexingConfig> relSourceAttributes = relationConfig.getSourceAttributes();
                    if (relSourceAttributes != null && !relSourceAttributes.isEmpty()) {
                        relation.loadOcurrences(entityModelCache.getNamesByIdMap(FieldType.class));
                        logger.debug("Loaded {} relation attribute configs for relation: {}", relSourceAttributes.size(), relation.getId());
                    }
                    relationCount++;
                }
            }
            
            logger.debug("Preload completed for entity: {}. Total relations processed: {}", entity.getId(), relationCount);
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
                throw new EntityIndexingException("Error mapping entity: " + entity.getId() +
                        " RDF mapping config for " + entityTypeName + " EntityType not found");
            }

            String entityId = entity.getId().toString();
            List<AttributeIndexingConfig> sourceAttributes = entityIndexingConfig.getSourceAttributes();

            // Procesar atributos (ya pre-cargados)
            if (sourceAttributes != null && !sourceAttributes.isEmpty()) {
                logger.debug("Processing {} attributes for entity: {}", sourceAttributes.size(), entity.getId());
                processAttributeList(sourceAttributes, entity);
            }

            // Procesar relaciones (ya pre-cargadas)
            int relationConfigCount = 0;
            for (RelationIndexingConfig relationConfig : entityIndexingConfig.getSourceRelations()) {
                String relationName = relationConfig.getName();
                Boolean isFromMember = entityModelCache.isFromRelation(relationName, entityTypeName);
                logger.debug("Processing relation config {} for entity: {}", relationName, entity.getId());

                // Obtener las relaciones ya cargadas desde el cache/contexto transaccional
                Set<Relation> relations = entityDataService.getRelationsWithThisEntityAsMember(entity.getId(), relationName, isFromMember);
                logger.debug("Processing {} relations of type {} for entity: {}", relations.size(), relationName, entity.getId());
                
                for (Relation relation : relations) {
                    String relationId = relation.getId().toString();
                    Entity relatedEntity = relation.getRelatedEntity(entity.getId());
                    String relatedEntityId = relatedEntity.getId().toString();
                    logger.debug("Processing relation: {} -> {}", relationId, relatedEntityId);

                    List<RDFTripleConfig> triplesConfig = relationConfig.getTargetTriples();
                    logger.debug("Processing {} target triples for relation: {}", triplesConfig.size(), relationId);
                    
                    for (RDFTripleConfig tripleConfig : triplesConfig) {
                        processTargetTriple(tripleConfig, null, entityId, relationId, relatedEntityId, null);
                    }

                    List<AttributeIndexingConfig> relSourceAttributes = relationConfig.getSourceAttributes();
                    if (relSourceAttributes != null && !relSourceAttributes.isEmpty()) {
                        logger.debug("Processing {} relation attributes for relation: {}", relSourceAttributes.size(), relationId);
                        processRelationAttributeList(relSourceAttributes, relation);
                    }
                }
                relationConfigCount++;
            }
            
            logger.debug("Internal processing completed for entity: {}. Processed {} relation configs", entity.getId(), relationConfigCount);
            
        } catch (CacheException | EntityRelationException e) {
            logger.error("Cache/Relation error for entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Indexing error for entity: " + entity.getId() + ". " + e.getMessage());
        } catch (Exception e) { 
            logger.error("Unexpected error processing entity {}: {}", entity.getId(), e.getMessage(), e);
            throw new EntityIndexingException("Unexpected error indexing entity: " + entity.getId() + ". " + e.getMessage());  
        }
    }

    @Override
    public void delete(String entityId) throws EntityIndexingException {
        logger.warn("Delete operation not yet implemented for TDB2ThreadedIndexer.");
    }

    @Override
    public void deleteAll(Collection<String> idList) throws EntityIndexingException {
        logger.warn("DeleteAll operation not yet implemented for TDB2ThreadedIndexer.");
    }

    @Override
    public void flush() {
        synchronized (flushLock) {
            logger.info("Starting flush operation...");

            // 1. Wait for all active indexing tasks (producers) to finish.
            logger.info("Waiting for active indexing threads to complete...");
            int phase = activeIndexingPhaser.arrive();
            try {
                activeIndexingPhaser.awaitAdvanceInterruptibly(phase);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Flush was interrupted while waiting for producers.", e);
                return;
            }
            logger.info("All indexing threads have completed.");

            // 2. Send a flush marker and wait for all writers to process it.
            CountDownLatch flushLatch = new CountDownLatch(writerThreads.size());
            try {
                logger.debug("Queueing flush marker.");
                tripleBuffer.put(new FlushMarker(flushLatch));
                logger.debug("Waiting for writers to acknowledge flush...");
                flushLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Flush was interrupted while waiting for writers.", e);
            }
            logger.info("Flush operation completed successfully.");
        }
    }

    private String extractXMLBody(String rdfxml) {
        int start = rdfxml.indexOf("<rdf:RDF");
        if (start == -1) return "";

        start = rdfxml.indexOf('>', start) + 1;
        int end = rdfxml.lastIndexOf("</rdf:RDF>");
        if (end == -1 || start >= end) return "";

        return rdfxml.substring(start, end);
    }

    private void clearTDBStore() {
        logger.info("Reset option set to true. Clearing graph in TDB2 store...");
        
        if (dataset == null) {
            logger.error("Dataset is null, cannot clear TDB store.");
            return;
        }

        dataset.begin(ReadWrite.WRITE);
        Model localModelToClear;
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
            logger.info("Graph cleared successfully. {} triples removed.", totalTriples);

        } catch (Exception e) {
            logger.error("Error clearing TDB2 graph: " + e.getMessage(), e);
            if (dataset.isInTransaction()) {
                dataset.abort();
            }
        } finally {
            if (dataset.isInTransaction()) { 
                 dataset.end();
            }
        }
        
        // Limpiar solo el TDB2 store - sin caches adicionales
        logger.info("Graph clearing process finished.");
    }
    
    /**
     * Realizar compactación del TDB2 store para optimizar espacio
     */
    public void compactTDBStore() {
        if (!hasTriplestore || dataset == null) {
            logger.warn("Cannot compact TDB store: no triplestore configured");
            return;
        }
        
        logger.info("TDB2 compaction not available in this version. Consider manual compaction if needed.");
        logger.info("Current TDB2 stats: {}", getTDB2Stats());
    }
    
    /**
     * Obtener estadísticas del TDB2 store
     */
    public TDB2Stats getTDB2Stats() {
        if (!hasTriplestore || dataset == null) {
            return new TDB2Stats(0);
        }
        
        dataset.begin(ReadWrite.READ);
        try {
            Model model = (this.graph == null || this.graph.isEmpty()) ?
                    dataset.getDefaultModel() :
                    dataset.getNamedModel(this.graph);
            
            long tripleCount = model.size();
            
            return new TDB2Stats(tripleCount);
            
        } finally {
            dataset.end();
        }
    }
    
    /**
     * Clase para estadísticas del TDB2 store
     */
    public static class TDB2Stats {
        private final long tripleCount;
        
        public TDB2Stats(long tripleCount) {
            this.tripleCount = tripleCount;
        }
        
        public long getTripleCount() { return tripleCount; }
        
        @Override
        public String toString() {
            return String.format("TDB2Stats{triples=%d}", tripleCount);
        }
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
        if (parts.length < 2 || parts[1].trim().isEmpty()) return value;
        String namespaceURI = namespaces.get(parts[0]);
        String localName = parts[1];
        return (namespaceURI != null ? namespaceURI : parts[0] + ":") + localName; 
    }

    private String buildIndividualUri(String namespaceKey, String prefix, String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warn("Cannot build individual URI with null or empty ID. NamespaceKey: " + namespaceKey + ", Prefix: " + prefix);
            return "urn:uuid:" + createRandomId();
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
        String sourceEntityId = null;

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
            return sourceEntityId;
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
        logger.debug("Processing {} attribute configs for entity: {}", attributeConfigs.size(), elementId);
        
        for (AttributeIndexingConfig attributeConfig : attributeConfigs) {
            logger.debug("Processing attribute: {} for entity: {}", attributeConfig.getName(), elementId);
            
            Collection<FieldOccurrence> fieldOccrs = entity.getFieldOccurrences(attributeConfig.getName());
            if (fieldOccrs == null || fieldOccrs.isEmpty()) {
                logger.debug("No field occurrences found for attribute: {} in entity: {}", attributeConfig.getName(), elementId);
                continue;
            }

            logger.debug("Found {} field occurrences for attribute: {}", fieldOccrs.size(), attributeConfig.getName());

            if (fieldOccurrenceFilterService != null && attributeConfig.getFilter() != null) {
                Map<String, String> filterParams = new HashMap<>(attributeConfig.getParams()); 
                filterParams.put("field", attributeConfig.getName());
                if (attributeConfig.getPreferredValuesOnly()) {
                    filterParams.put("preferred", "true");
                }
                int beforeFilter = fieldOccrs.size();
                fieldOccrs = fieldOccurrenceFilterService.filter(fieldOccrs, attributeConfig.getFilter(), filterParams);
                logger.debug("Filter applied to attribute {}: {} -> {} occurrences", attributeConfig.getName(), beforeFilter, fieldOccrs.size());
            }

            for (FieldOccurrence fieldOccr : fieldOccrs) {
                try {
                    if (!attributeConfig.getSubAttributes().isEmpty()) {
                        logger.debug("Processing {} sub-attributes for occurrence", attributeConfig.getSubAttributes().size());
                        for (AttributeIndexingConfig subAttribute : attributeConfig.getSubAttributes()) {
                            String occrValue = fieldOccr.getValue(subAttribute.getName());
                            if (occrValue != null) {
                                logger.debug("Processing sub-attribute: {} with value: {}", subAttribute.getName(), occrValue);
                                processEntityAttribute(occrValue, subAttribute, elementId);
                            }
                        }
                    } else {
                        String occrValue = fieldOccr.getValue();
                         if (occrValue != null) { 
                            logger.debug("Processing attribute: {} with value: {}", attributeConfig.getName(), occrValue);
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
        logger.debug("Processing entity attribute: {} = {} for element: {}", attributeConfig.getName(), occrValue, elementId);
        
        String alternativeId = createRandomId();
        List<RDFTripleConfig> triplesConfig = attributeConfig.getTargetTriples();
        logger.debug("Processing {} target triples for attribute", triplesConfig.size());
        
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
        logger.debug("Processing target triple - Entity: {}, Relation: {}, RelatedEntity: {}, Value: {}", 
            entityId, relationId, relatedEntityId, occrValue);
        
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
        logger.debug("Subject URI: {}, Type: {}", subjectUri, subjectTypeUri);

        String predicateUri = expandElementUri(predicateConfig.getValue());
        String predicateType = predicateConfig.getType(); 
        logger.debug("Predicate URI: {}, Type: {}", predicateUri, predicateType);

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
            logger.debug("Object property - URI: {}, Type: {}", objectValue, objectTypeUri);
        } else { 
            if (objectConfig.getType() != null) { 
                objectTypeUri = expandElementUri(objectConfig.getType());
            }
            objectValue = ATTR_VALUE.equals(objectConfig.getValue()) ? occrValue : objectConfig.getValue();
            if (objectValue == null && ATTR_VALUE.equals(objectConfig.getValue())) {
                 logger.debug("Occurrence value is null for a data property, skipping triple generation for subject: " + subjectUri + ", predicate: " + predicateUri);
                 return; 
            }
            logger.debug("Data property - Value: {}, Type: {}", objectValue, objectTypeUri);
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
        if (subjectUri == null || predicateUri == null || objectValue == null) {
            logger.warn("Skipping triple due to null part: S={}, P={}, O={}", subjectUri, predicateUri, objectValue);
            return;
        }

        // Usar modelo compartido para mantener identidad de recursos y mejorar deduplicación TDB2
        Statement statement;
        synchronized (sharedModelLock) {
            Resource subject = sharedResourceModel.createResource(subjectUri);
            if (subjectTypeUri != null) {
                // Agregar tipo como statement separado si no existe
                Resource typeResource = sharedResourceModel.createResource(subjectTypeUri);
                Statement typeStatement = sharedResourceModel.createStatement(subject, RDF.type, typeResource);
                if (!sharedResourceModel.contains(typeStatement)) {
                    try {
                        tripleBuffer.put(typeStatement);
                        triplesProduced.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted while adding type statement to buffer.", e);
                        return;
                    }
                }
            }

            Property predicate = sharedResourceModel.createProperty(predicateUri);
            
            RDFNode object;
            if (OBJECT_PROPERTY.equals(predicateType)) {
                object = sharedResourceModel.createResource(objectValue);
                if (objectTypeUri != null) {
                    // Agregar tipo del objeto como statement separado si no existe
                    Resource objectTypeResource = sharedResourceModel.createResource(objectTypeUri);
                    Statement objectTypeStatement = sharedResourceModel.createStatement((Resource)object, RDF.type, objectTypeResource);
                    if (!sharedResourceModel.contains(objectTypeStatement)) {
                        try {
                            tripleBuffer.put(objectTypeStatement);
                            triplesProduced.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("Interrupted while adding object type statement to buffer.", e);
                            return;
                        }
                    }
                }
            } else {
                object = sharedResourceModel.createLiteral(objectValue);
            }

            statement = sharedResourceModel.createStatement(subject, predicate, object);
        }

        try {
            // Enviar statement principal al buffer
            tripleBuffer.put(statement);
            triplesProduced.incrementAndGet();
            logger.debug("Triple added to buffer: S={}, P={}, O={}", subjectUri, predicateUri, objectValue);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting to add triple to buffer.", e);
        }
    }
    
    @Override
    public void close() throws IOException {
        synchronized (flushLock) {
            if (shutdown) {
                return;
            }
            logger.info("Closing EntityIndexerTDB2ThreadedImpl...");
            shutdown = true;

            // 1. Wait for any running producers to finish
            logger.info("Waiting for final indexing tasks to complete...");
            int phase = activeIndexingPhaser.arrive();
            try {
                activeIndexingPhaser.awaitAdvanceInterruptibly(phase);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for producers during close.", e);
            }
            logger.info("All indexing tasks finished.");

            try {
                // 2. Signal distributor and writers to shut down
                logger.info("Signaling shutdown to distributor and writers...");
                tripleBuffer.put(POISON_PILL);

                // 3. Wait for distributor to finish
                if (distributorThread != null) {
                    distributorThread.join();
                    logger.info("Distributor thread finished.");
                }

                // 4. Wait for all writer threads to finish
                for (Thread writerThread : writerThreads) {
                    writerThread.join();
                }
                logger.info("All writer threads finished.");

                // 5. Close all writers
                for (Closeable writer : writers) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        logger.error("Error closing writer", e);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted during indexer shutdown.", e);
            } finally {
                // 6. Shutdown executors
                shutdownExecutor(indexingExecutor, "Indexing");
                shutdownExecutor(utilityExecutor, "Utility");
                
                // 7. Clean up shared resource model
                synchronized (sharedModelLock) {
                    if (sharedResourceModel != null) {
                        sharedResourceModel.close();
                        sharedResourceModel = null;
                        logger.info("Shared resource model closed.");
                    }
                }

                // 8. Close TDB dataset
                if (hasTriplestore && dataset != null) {
                    if (dataset.isInTransaction()) {
                        logger.warn("Transaction was still active during close. Attempting to end.");
                        dataset.end();
                    }
                    dataset.close();
                    logger.info("TDB2 Dataset closed.");
                }
                logger.info("EntityIndexerTDB2ThreadedImpl closed successfully.");
            }
        }
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null) {
            logger.debug("Shutting down {} executor", name);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("{} executor did not terminate gracefully, forcing shutdown", name);
                    List<Runnable> pending = executor.shutdownNow();
                    logger.warn("{} pending tasks cancelled in {} executor.", pending.size(), name);
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for {} executor termination", name);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Obtener estadísticas de procesamiento simplificadas
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
                tripleBuffer != null ? tripleBuffer.size() : 0,
                bufferSize,
                Math.max(0, activeIndexingPhaser.getRegisteredParties() - 1), // Restar el hilo principal
                triplesProduced.get(),
                triplesConsumed.get(),
                triplesWritten.get(),
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
        private final long triplesProduced;
        private final long triplesConsumed;
        private final long triplesWritten;
        private final int availableSlots;
        private final int maxSlots;

        public ProcessingStats(int bufferSize, int bufferCapacity, int activeTasks, long triplesProduced, 
                             long triplesConsumed, long triplesWritten, int availableSlots, int maxSlots) {
            this.bufferSize = bufferSize;
            this.bufferCapacity = bufferCapacity;
            this.activeTasks = activeTasks;
            this.triplesProduced = triplesProduced;
            this.triplesConsumed = triplesConsumed;
            this.triplesWritten = triplesWritten;
            this.availableSlots = availableSlots;
            this.maxSlots = maxSlots;
        }

        public int getBufferSize() { return bufferSize; }
        public int getBufferCapacity() { return bufferCapacity; }
        public int getActiveTasks() { return Math.max(0, activeTasks); }
        public long getTriplesProduced() { return triplesProduced; }
        public long getTriplesConsumed() { return triplesConsumed; }
        public long getTriplesWritten() { return triplesWritten; }
        public int getAvailableSlots() { return availableSlots; }
        public int getMaxSlots() { return maxSlots; }
        public int getUsedSlots() { return maxSlots - availableSlots; }
        public double getBufferUsagePercentage() {
            return bufferCapacity > 0 ? (double) bufferSize * 100.0 / bufferCapacity : 0.0;
        }
    }

    /**
     * The Distributor thread takes items from the main buffer and distributes them
     * to all output queues.
     */
    private class Distributor implements Runnable {
        @Override
        public void run() {
            logger.info("Distributor thread started.");
            try {
                while (!shutdown) {
                    Object item = tripleBuffer.take();
                    
                    if (item instanceof Statement) {
                        triplesConsumed.incrementAndGet();
                    }

                    if (item == POISON_PILL) {
                        logger.info("Distributor received POISON_PILL. Forwarding to writers.");
                        for (BlockingQueue<Object> queue : outputQueues) {
                            queue.put(POISON_PILL);
                        }
                        break; // Exit run loop
                    }

                    // Distribute to all output queues
                    for (BlockingQueue<Object> queue : outputQueues) {
                        queue.put(item);
                    }
                }
            } catch (InterruptedException e) {
                if (!shutdown) {
                    logger.error("Distributor thread was interrupted.", e);
                }
                Thread.currentThread().interrupt();
            }
            logger.info("Distributor thread is shutting down.");
        }
    }

    /**
     * Abstract base class for writer threads with reduced batch sizes for stability.
     */
    private abstract class AbstractWriter implements Runnable, Closeable {
        protected final BlockingQueue<Object> queue;
        protected final List<Statement> localBuffer;
        protected final int batchSize = 250; // Reducir significativamente el tamaño de batch
        protected final long maxWaitTimeMs = 1000; // Reducir tiempo de espera

        protected AbstractWriter(BlockingQueue<Object> queue) {
            this.queue = queue;
            this.localBuffer = new ArrayList<>(batchSize);
        }

        @Override
        public void run() {
            logger.info("{} started.", this.getClass().getSimpleName());
            long lastWriteTime = System.currentTimeMillis();
            
            try {
                while (true) {
                    // Usar poll con timeout en lugar de take bloqueante
                    Object item = queue.poll(500, TimeUnit.MILLISECONDS);
                    
                    if (item != null) {
                        if (item instanceof Statement) {
                            localBuffer.add((Statement) item);
                            
                            // Escribir si se llena el batch o ha pasado suficiente tiempo
                            if (localBuffer.size() >= batchSize || 
                                (System.currentTimeMillis() - lastWriteTime) > maxWaitTimeMs) {
                                writeBatch();
                                lastWriteTime = System.currentTimeMillis();
                            }
                        } else if (item instanceof FlushMarker) {
                            writeBatch(); // Write remaining triples
                            lastWriteTime = System.currentTimeMillis();
                            ((FlushMarker) item).latch.countDown();
                        } else if (item == POISON_PILL) {
                            writeBatch(); // Write final batch
                            break; // Exit run loop
                        }
                    } else {
                        // Timeout - escribir si hay datos pendientes y ha pasado tiempo suficiente
                        if (!localBuffer.isEmpty() && 
                            (System.currentTimeMillis() - lastWriteTime) > maxWaitTimeMs) {
                            writeBatch();
                            lastWriteTime = System.currentTimeMillis();
                        }
                    }
                }
            } catch (InterruptedException e) {
                if (!shutdown) {
                    logger.error("Writer thread was interrupted.", e);
                }
                Thread.currentThread().interrupt();
            } finally {
                if (!localBuffer.isEmpty()) {
                    logger.warn("Writing remaining {} triples from local buffer on thread exit.", localBuffer.size());
                    writeBatch();
                }
                logger.info("{} is shutting down.", this.getClass().getSimpleName());
            }
        }

        private void writeBatch() {
            if (localBuffer.isEmpty()) {
                return;
            }
            int batchSizeToWrite = localBuffer.size();
            try {
                performWrite(new ArrayList<>(localBuffer)); // Create copy to avoid concurrent modification
                logger.debug("{} wrote batch of {} triples", this.getClass().getSimpleName(), batchSizeToWrite);
            } catch (Exception e) {
                logger.error("Error writing batch in {}", this.getClass().getSimpleName(), e);
            } finally {
                localBuffer.clear();
            }
        }

        protected abstract void performWrite(List<Statement> batch);
    }

    /**
     * Writer for TDB2 dataset with enhanced thread safety and connection pooling.
     */
    private class TDBWriter extends AbstractWriter {
        private final Dataset tdbDataset;
        private final String graphName;
        private final Object tdbWriteLock = new Object(); // Serializar acceso a TDB2

        TDBWriter(BlockingQueue<Object> queue, Dataset tdbDataset, String graphName) {
            super(queue);
            this.tdbDataset = tdbDataset;
            this.graphName = graphName;
        }

        @Override
        protected void performWrite(List<Statement> batch) {
            if (tdbDataset == null || !hasTriplestore || batch.isEmpty()) return;
            
            // Serializar completamente el acceso a TDB2 para evitar corrupción
            synchronized (tdbWriteLock) {
                boolean transactionStarted = false;
                try {
                    // Verificar estado del dataset antes de comenzar transacción
                    if (tdbDataset.isInTransaction()) {
                        logger.warn("TDBWriter: Dataset already in transaction, waiting for completion");
                        Thread.sleep(100); // Pequeña pausa
                        return; // Reintentará en la próxima iteración
                    }
                    
                    tdbDataset.begin(ReadWrite.WRITE);
                    transactionStarted = true;
                    
                    Model model = (graphName != null && !graphName.isEmpty()) ?
                            tdbDataset.getNamedModel(graphName) :
                            tdbDataset.getDefaultModel();
                    
                    // Escribir directamente sin verificación de duplicados - TDB2 maneja esto automáticamente
                    // Escribir en lotes pequeños para mejor estabilidad
                    int maxBatchSize = 100;
                    List<Statement> currentBatch = new ArrayList<>();
                    int batchNumber = 0;
                    
                    for (Statement stmt : batch) {
                        currentBatch.add(stmt);
                        
                        if (currentBatch.size() >= maxBatchSize) {
                            model.add(currentBatch);
                            logger.debug("TDBWriter wrote sub-batch {} with {} statements", batchNumber, currentBatch.size());
                            
                            currentBatch.clear();
                            batchNumber++;
                            
                            // Pausa para permitir que otros threads accedan
                            if (batch.size() > maxBatchSize && batchNumber % 5 == 0) {
                                Thread.sleep(10); // Pausa cada 5 sub-lotes
                            } else {
                                Thread.yield();
                            }
                        }
                    }
                    
                    // Escribir lote restante
                    if (!currentBatch.isEmpty()) {
                        model.add(currentBatch);
                        logger.debug("TDBWriter wrote final sub-batch with {} statements", currentBatch.size());
                    }
                    
                    tdbDataset.commit();
                    transactionStarted = false;
                    
                    // Incrementar contador de triples escritas
                    triplesWritten.addAndGet(batch.size());
                    logger.debug("TDBWriter completed writing {} triples to TDB2 in {} sub-batches. TDB2 handles duplicate detection automatically.", 
                               batch.size(), batchNumber + 1);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("TDBWriter interrupted during write operation");
                    if (transactionStarted && tdbDataset.isInTransaction()) {
                        try {
                            tdbDataset.abort();
                        } catch (Exception abortEx) {
                            logger.error("Error aborting TDB2 transaction", abortEx);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error writing batch to TDB2 dataset: {}", e.getMessage(), e);
                    if (transactionStarted && tdbDataset.isInTransaction()) {
                        try {
                            tdbDataset.abort();
                        } catch (Exception abortEx) {
                            logger.error("Error aborting TDB2 transaction after write failure", abortEx);
                        }
                    }
                    // No re-throw para evitar crash del writer thread
                } finally {
                    // Asegurar que la transacción se cierre correctamente
                    if (transactionStarted && tdbDataset.isInTransaction()) {
                        try {
                            tdbDataset.end();
                        } catch (Exception endEx) {
                            logger.error("Error ending TDB2 transaction", endEx);
                        }
                    }
                }
            }
        }
        
        @Override
        public void close() {
            synchronized (tdbWriteLock) {
                logger.info("TDBWriter closed");
                // TDB dataset is closed centrally, nothing to do here.
            }
        }
    }

    /**
     * Writer for XML files.
     */
    private class XMLWriter extends AbstractWriter {
        private final String filePath;
        private final Map<String, String> namespaces;
        private FileOutputStream fos;
        private boolean headerWritten = false;
        private final Object writeLock = new Object();

        XMLWriter(BlockingQueue<Object> queue, OutputConfig config, Map<String, String> namespaces) throws IOException {
            super(queue);
            this.filePath = config.getPath() + config.getName();
            this.namespaces = new HashMap<>(namespaces); // Create defensive copy
            
            java.io.File file = new java.io.File(this.filePath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            
            boolean reset = "true".equalsIgnoreCase(config.getReset());
            this.fos = new FileOutputStream(file, !reset);
            
            // Check if file already has content
            try {
                if (fos.getChannel().position() > 0) {
                    this.headerWritten = true;
                }
            } catch (IOException e) {
                logger.warn("Could not check file position for {}", this.filePath, e);
            }
        }

        @Override
        protected void performWrite(List<Statement> batch) {
            if (batch.isEmpty()) return;
            
            synchronized (writeLock) {
                try {
                    // Write header if needed
                    if (!headerWritten && fos.getChannel().position() == 0) {
                        writeXMLHeader();
                        headerWritten = true;
                    }

                    // Write batch content
                    Model batchModel = ModelFactory.createDefaultModel();
                    batchModel.add(batch);

                    StringWriter sw = new StringWriter();
                    batchModel.write(sw, "RDF/XML-ABBREV");
                    String body = extractXMLBody(sw.toString());
                    if (body != null && !body.trim().isEmpty()) {
                        fos.write(body.getBytes("UTF-8"));
                        fos.flush(); // Ensure data is written
                        
                        // Incrementar contador de triples escritas
                        triplesWritten.addAndGet(batch.size());
                        logger.debug("XMLWriter wrote {} triples to file: {}", batch.size(), filePath);
                    }
                } catch (IOException e) {
                    logger.error("Failed to write batch to XML file: " + filePath, e);
                }
            }
        }
        
        private void writeXMLHeader() throws IOException {
            Model tempModel = ModelFactory.createDefaultModel();
            tempModel.setNsPrefixes(this.namespaces);
            StringWriter sw = new StringWriter();
            tempModel.write(sw, "RDF/XML");
            String header = sw.toString().replaceAll("</rdf:RDF>\\s*$", "");
            fos.write(header.getBytes("UTF-8"));
        }

        @Override
        public void close() throws IOException {
            synchronized (writeLock) {
                if (fos != null) {
                    try {
                        if (headerWritten) {
                            fos.write("\n</rdf:RDF>".getBytes("UTF-8"));
                        }
                        fos.flush();
                    } catch (IOException e) {
                        logger.error("Error writing XML footer to file: " + filePath, e);
                    } finally {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            logger.error("Error closing file output stream for: " + filePath, e);
                        }
                        fos = null;
                    }
                }
            }
        }
    }
    
    private void initializeSharedResourceModel() {
        synchronized (sharedModelLock) {
            sharedResourceModel = ModelFactory.createDefaultModel();
            // Configurar namespaces para el modelo compartido
            if (namespaces != null) {
                sharedResourceModel.setNsPrefixes(namespaces);
            }
            logger.info("Shared resource model initialized - will be preserved throughout execution for optimal deduplication");
        }
    }
}