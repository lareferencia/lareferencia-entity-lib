package org.lareferencia.core.entity.workers;

import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.EntityIndexingService;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityLoadingMonitorService;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.worker.AbstractFlowableWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import lombok.Getter;
import lombok.Setter;

@Component("entityIndexingWorker")
@Scope("prototype")
public class EntityIndexingWorker extends AbstractFlowableWorker {

    private static Logger logger = LogManager.getLogger(EntityIndexingWorker.class);

    @Autowired
    EntityDataService erService;

    @Autowired
    EntityRepository entityRepository;

    @Autowired
    EntityIndexingService indexingService;

    @Autowired
    EntityLoadingMonitorService entityMonitorService;

    @Autowired
    EntityTypeRepository entityTypeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Flowable Expressions
    private Expression enableProfiling;
    private Expression deleteMode;
    private Expression entityType;
    private Expression provenanceSource;
    private Expression lastUpdate; // Date
    private Expression pageSize;
    private Expression fromPage;
    private Expression indexingConfigFile;
    private Expression indexerBeanName;

    // Internal State Class
    private static class IndexingContext {
        boolean enableProfilingBool = true;
        boolean deleteModeBool = false;
        String entityTypeStr;
        String provenanceSourceStr;
        Date lastUpdateDate;
        int pageSizeInt = 100;
        int fromPageInt = 0;
        String indexingConfigFileStr;
        String indexerBeanNameStr;

        EntityPaginator entityPaginator;
        IEntityIndexer indexer;
        Profiler profiler;
        boolean emptyPage = true;

        int actualPage = 0;
        int totalPages = 1;
        boolean wasStopped = false;
    }

    NumberFormat percentajeFormat = NumberFormat.getPercentInstance();

    @Override
    protected void executeWorker(DelegateExecution execution) throws Exception {

        IndexingContext context = new IndexingContext();

        // Parameter Resolution
        if (enableProfiling != null)
            context.enableProfilingBool = (Boolean) enableProfiling.getValue(execution);
        if (deleteMode != null)
            context.deleteModeBool = (Boolean) deleteMode.getValue(execution);
        if (entityType != null)
            context.entityTypeStr = (String) entityType.getValue(execution);
        if (provenanceSource != null)
            context.provenanceSourceStr = (String) provenanceSource.getValue(execution);
        if (lastUpdate != null)
            context.lastUpdateDate = (Date) lastUpdate.getValue(execution); // Assuming Date object
        if (pageSize != null)
            context.pageSizeInt = ((Number) pageSize.getValue(execution)).intValue();
        if (fromPage != null)
            context.fromPageInt = ((Number) fromPage.getValue(execution)).intValue();
        if (indexingConfigFile != null)
            context.indexingConfigFileStr = (String) indexingConfigFile.getValue(execution);
        if (indexerBeanName != null)
            context.indexerBeanNameStr = (String) indexerBeanName.getValue(execution);

        // --- Pre Run ---
        preRun(context);

        // --- Run Loop (Manual Transaction Management) ---
        if (context.entityPaginator != null) {

            context.totalPages = context.entityPaginator.getTotalPages();
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
            definition.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
            // definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            // // Maybe?

            for (context.actualPage = context.entityPaginator
                    .getStartingPage(); context.actualPage <= context.totalPages
                            && !context.wasStopped; context.actualPage++) {

                TransactionStatus transactionStatus = null;
                logger.info("WORKER: EntityIndexing :: Processing page: " + context.actualPage + " of "
                        + context.totalPages);

                try {
                    // Manual transaction start
                    transactionStatus = transactionManager.getTransaction(definition);

                    prePage(context);

                    Page<Entity> page = context.entityPaginator.nextPage();
                    List<Entity> items = page.getContent();

                    for (Entity item : items) {
                        if (context.wasStopped)
                            break;
                        try {
                            processItem(item, context);
                        } catch (Exception e) {
                            throw new RuntimeException("Runtime error processing in item: " + item.toString() + " : "
                                    + e.getClass().toString() + "::" + e.getMessage());
                        }
                    }

                    if (!context.wasStopped) {
                        postPage(context);
                        transactionManager.commit(transactionStatus);
                    } else {
                        transactionManager.rollback(transactionStatus);
                    }

                } catch (Exception e) {
                    logger.error(e);
                    context.wasStopped = true;
                    if (transactionStatus != null)
                        transactionManager.rollback(transactionStatus);
                    // rethrow or handle?
                    throw e;
                }
            }

            if (!context.wasStopped)
                postRun(context);
        }

        logger.info("WORKER: EntityIndexing :: END processing");

    }

    private void preRun(IndexingContext context) {
        try {

            // Convert Date to LocalDateTime if needed
            java.time.LocalDateTime lastUpdateLDT = null;
            if (context.lastUpdateDate != null) {
                lastUpdateLDT = context.lastUpdateDate.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            org.lareferencia.core.entity.domain.EntityType eType = null;
            if (context.entityTypeStr != null) {
                eType = entityTypeRepository.findOneByName(context.entityTypeStr).orElse(null);
                if (eType == null) {
                    throw new RuntimeException("EntityType not found: " + context.entityTypeStr);
                }
            }

            if (eType != null) {
                if (context.provenanceSourceStr != null) {
                    logger.info("Getting entities of type: " + context.entityTypeStr + " and provenance source: "
                            + context.provenanceSourceStr);
                    context.entityPaginator = new EntityPaginator(entityRepository, eType, context.provenanceSourceStr);
                } else {
                    if (lastUpdateLDT != null) {
                        logger.info("Getting entities of type: " + context.entityTypeStr + " and last update: "
                                + lastUpdateLDT);
                        context.entityPaginator = new EntityPaginator(entityRepository, eType, lastUpdateLDT);
                    } else {
                        logger.info("Getting entities of type: " + context.entityTypeStr);
                        // context.entityPaginator = new EntityPaginator(entityRepository, eType); //
                        // Constructor missing?
                        // Assuming constructor exists or fallback
                        context.entityPaginator = new EntityPaginator(entityRepository, eType);
                    }
                }
            } else {
                if (lastUpdateLDT != null) {
                    logger.info("Getting all entities from last update: " + lastUpdateLDT);
                    context.entityPaginator = new EntityPaginator(entityRepository, lastUpdateLDT);
                } else {
                    logger.info("Getting all entities");
                    context.entityPaginator = new EntityPaginator(entityRepository);
                }
            }

            // set page size
            context.entityPaginator.setPageSize(context.pageSizeInt);
            context.entityPaginator.setActualPage(context.fromPageInt);

            // this.setPaginator(entityPaginator); // removed
            logger.info("Total pages of size: " + context.entityPaginator.getPageSize() + " to index: "
                    + context.entityPaginator.getTotalPages());

            context.indexer = indexingService.getIndexer(context.indexingConfigFileStr, context.indexerBeanNameStr);

        } catch (Exception e) {
            logError("Error in Entity Indexing Init: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void prePage(IndexingContext context) {
        context.profiler = new Profiler(context.enableProfilingBool, "").start();
        context.emptyPage = true;

        try {
            context.indexer.prePage();
        } catch (EntityIndexingException e) {
            logError("Error in indexer prePage: " + e.getMessage());
        }

        // Log the start of the page processing
        logInfo("Processing page: " + context.actualPage + " of type: " + context.entityTypeStr + " with size: "
                + context.entityPaginator.getPageSize());
    }

    private void processItem(Entity entity, IndexingContext context) {
        try {
            // Delete or index depending
            if (context.deleteModeBool)
                context.indexer.delete(entity.getId().toString());
            else {
                context.indexer.index(entity);
                entityMonitorService.addEntitySentToIndex(entity.getId(), entity.getEntityTypeId());
            }
            context.emptyPage = false;
        } catch (Exception e) {
            entityMonitorService.reportEntityIndexingError(entity.getId(), e.getMessage());
            String msg = "Error indexing entity internal EntityTypeID: " + entity.getId() + " -- msg: "
                    + e.getMessage();
            e.printStackTrace();
            logError(msg);
        }
    }

    private void postPage(IndexingContext context) {
        // Execute only if at least one entity was indexed
        if (!context.emptyPage) {
            try {
                context.indexer.flush();
            } catch (EntityIndexingException e) {
                logError("Error indexing page: " + context.actualPage + " ::" + e.getMessage());
            }
        }

        context.profiler.messure("Page Indexing Time (ms): ", false);
        context.profiler.report(logger);
    }

    private void postRun(IndexingContext context) {
        logInfo("EntityRelationIndexing worker :: FINISHED");

        // Cerrar el indexer para liberar todos los recursos (threads, conexiones, etc.)
        if (context.indexer != null) {
            try {
                logInfo("Closing indexer and releasing resources...");
                if (context.indexer instanceof java.io.Closeable) {
                    ((java.io.Closeable) context.indexer).close();
                    logInfo("Indexer closed successfully - all resources released");
                }
            } catch (Exception e) {
                logError("Error closing indexer: " + e.getMessage());
            }
        }
    }

    /******************* Auxiliares ********** */

    private void logError(String message) {
        logger.error(message);
    }

    private void logInfo(String message) {
        logger.info(message);
    }

}
