package org.lareferencia.core.entity.workers;

import java.text.NumberFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.EntityIndexingService;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityLoadingMonitorService;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import lombok.Getter;
import lombok.Setter;

public class EntityIndexingWorker extends BaseBatchWorker<Entity, EntityIndexingRunningContext> {

    private static Logger logger = LogManager.getLogger(EntityIndexingWorker.class);

    @Getter
    @Setter
    Boolean enableProfiling = true;

    @Autowired
    EntityDataService erService;

    @Autowired
    EntityRepository entityRepository;

    @Autowired
    EntityIndexingService indexingService;

    EntityPaginator entityPaginator;

    IEntityIndexer indexer;

    private Profiler profiler;

    NumberFormat percentajeFormat = NumberFormat.getPercentInstance();

    boolean emptyPage = true;

    @Autowired
    EntityLoadingMonitorService entityMonitorService;

    //private ExecutorService executorService;

    public EntityIndexingWorker() {
        super();
    }

    @Override
    public void preRun() {
        try {
            if (runningContext.getEntityType() != null) {
                if (runningContext.getProvenanceSource() != null) {
                    logger.info("Getting entities of type: " + runningContext.getEntityType() + " and provenance source: " + runningContext.getProvenanceSource());
                    entityPaginator = new EntityPaginator(entityRepository, runningContext.getEntityType(), runningContext.getProvenanceSource());
                } else {
                    if (runningContext.getLastUdate() != null) {
                        logger.info("Getting entities of type: " + runningContext.getEntityType() + " and last update: " + runningContext.getLastUdate());
                        entityPaginator = new EntityPaginator(entityRepository, runningContext.getEntityType(), runningContext.getLastUdate());
                    } else {
                        logger.info("Getting entities of type: " + runningContext.getEntityType());
                        entityPaginator = new EntityPaginator(entityRepository, runningContext.getEntityType());
                    }
                }
            } else {
                if (runningContext.getLastUdate() != null) {
                    logger.info("Getting all entities from last update: " + runningContext.getLastUdate());
                    entityPaginator = new EntityPaginator(entityRepository, runningContext.getLastUdate());
                } else {
                    logger.info("Getting all entities");
                    entityPaginator = new EntityPaginator(entityRepository);
                }
            }

            // set page size
            entityPaginator.setPageSize(runningContext.getPageSize());
            entityPaginator.setActualPage(runningContext.getFromPage());

            this.setPaginator(entityPaginator);
            logger.info("Total pages of size: " + entityPaginator.getPageSize() + " to index: " + entityPaginator.getTotalPages());

            indexer = indexingService.getIndexer(runningContext.getIndexingConfigFile(), runningContext.getIndexerBeanName());

            // Initialize the executor service with a fixed thread pool
            //executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        } catch (Exception e) {
            logError("Error in Entity Indexing: " + runningContext.toString() + " :: " + e.getMessage());
            error();
        }
    }

    @Override
    public void prePage() {
        profiler = new Profiler(enableProfiling, "").start();
        emptyPage = true;
        
        try {
            indexer.prePage();
        } catch (EntityIndexingException e) {
            logError("Error in indexer prePage: " + e.getMessage());
        }

        // Log the start of the page processing
        logInfo("Processing page: " + this.getActualPage() + " of type: " + runningContext.getEntityType() + " with size: " + entityPaginator.getPageSize());
    }

    @Override
    @Transactional
    public void processItem(Entity entity) {

        //executorService.submit(() -> {
            try {
                // Delete or index depending
                if (runningContext.getDeleteMode())
                    indexer.delete(entity.getId().toString());
                else {
                    indexer.index(entity);
                    entityMonitorService.addEntitySentToIndex(entity.getId(), entity.getEntityTypeId());
                }
                emptyPage = false;
            } catch (Exception e) {
                entityMonitorService.reportEntityIndexingError(entity.getId(), e.getMessage());
                String msg = "Error indexing entity internal EntityTypeID: " + entity.getId() + " " + runningContext.toString() + " -- msg: " + e.getMessage();
                e.printStackTrace();
                logError(msg);
            }
        //});
    }

    @Override
    public void postPage() {
        // Wait for all tasks to complete
        // executorService.shutdown();
        // try {
        //     if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
        //         executorService.shutdownNow();
        //     }
        // } catch (InterruptedException e) {
        //     executorService.shutdownNow();
        // }

        // Execute only if at least one entity was indexed
        if (!emptyPage) {
            try {
                indexer.flush();
            } catch (EntityIndexingException e) {
                logError("Error indexing page: " + this.getActualPage() + " ::" + e.getMessage());
            }
        }

        profiler.messure("Page Indexing Time (ms): ", false);
        profiler.report(logger);
    }

    @Override
    public void postRun() {
        logInfo("EntityRelationIndexing worker :: FINISHED :: " + runningContext.toString());
        
        // Cerrar el indexer para liberar todos los recursos (threads, conexiones, etc.)
        if (indexer != null) {
            try {
                logInfo("Closing indexer and releasing resources...");
                if (indexer instanceof java.io.Closeable) {
                    ((java.io.Closeable) indexer).close();
                    logInfo("Indexer closed successfully - all resources released");
                }
            } catch (Exception e) {
                logError("Error closing indexer: " + e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        return "ERIndexer::" + "[" + percentajeFormat.format(this.getCompletionRate()) + "]";
    }

    /******************* Auxiliares ********** */

    private void error() {
        this.stop();
    }

    private void logError(String message) {
        logger.error(message);
    }

    private void logInfo(String message) {
        logger.info(message);
    }

   
}
