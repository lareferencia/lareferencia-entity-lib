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

package org.lareferencia.core.entity.workers;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.lareferencia.core.repository.parquet.RecordValidation;
import org.lareferencia.core.repository.parquet.ValidationStatParquetRepository;
import org.lareferencia.core.service.management.SnapshotLogService;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.metadata.IMDFormatTransformer;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.MDFormatTranformationException;
import org.lareferencia.core.metadata.MDFormatTransformerService;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.metadata.RecordStatus;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.worker.AbstractFlowableWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import lombok.Getter;
import lombok.Setter;

@Component("entityExtractionWorker")
@Scope("prototype")
public class EntityExtractionWorker extends AbstractFlowableWorker {

	private static Logger logger = LogManager.getLogger(EntityExtractionWorker.class);

	@Autowired
	private SnapshotLogService snapshotLogService;

	@Autowired
	private ISnapshotStore snapshotStore;

	@Autowired
	private IMetadataStore metadataStore;

	@Autowired
	private ValidationStatParquetRepository parquetRepository;

	@Autowired
	EntityDataService erService;

	@Autowired
	MDFormatTransformerService trfService;

	// Flowable Expressions
	private Expression targetSchemaName;
	private Expression debugMode;
	private Expression profileMode;
	private Expression entityCacheSize;

	NumberFormat percentajeFormat = NumberFormat.getPercentInstance();

	private static class ExtractionContext {
		Network network;
		Long snapshotId;
		SnapshotMetadata snapshotMetadata;
		List<RecordValidation> recordsToProcess;
		Integer currentRecordIndex = 0;
		Integer totalRecords = 0;
		Integer pageSize = 1000;
		IMDFormatTransformer metadataTransformer;
		Profiler profiler;

		String targetSchemaNameStr;
		boolean debugModeBool = false;
		boolean profileModeBool = false;
		Integer entityCacheSizeInt = null;

		long initialTime;
		long startTime;
		long endTime;
	}

	@Override
	protected void executeWorker(DelegateExecution execution) throws Exception {

		if (this.network == null) {
			throw new IllegalStateException("EntityExtractionWorker requires a valid Network context");
		}

		ExtractionContext context = new ExtractionContext();
		context.network = this.network;
		context.initialTime = System.nanoTime();

		// Parameter resolution
		if (targetSchemaName != null)
			context.targetSchemaNameStr = (String) targetSchemaName.getValue(execution);
		if (debugMode != null)
			context.debugModeBool = (Boolean) debugMode.getValue(execution);
		if (profileMode != null)
			context.profileModeBool = (Boolean) profileMode.getValue(execution);
		if (entityCacheSize != null)
			context.entityCacheSizeInt = ((Number) entityCacheSize.getValue(execution)).intValue();

		// --- Pre Run ---
		context.snapshotId = snapshotStore.findLastGoodKnownSnapshot(context.network);

		if (context.snapshotId != null) {
			context.snapshotMetadata = snapshotStore.getSnapshotMetadata(context.snapshotId);

			logger.debug("Full entity extraction on snapshot: " + context.snapshotId);
			logInfo("Full entity extraction: " + context.network.getAcronym() + " (" + context.targetSchemaNameStr
					+ ")");

			try {
				context.recordsToProcess = parquetRepository
						.getRecordValidationListBySnapshotAndStatus(context.snapshotId, RecordStatus.VALID);
				context.totalRecords = context.recordsToProcess.size();

				context.metadataTransformer = trfService.getMDTransformer(context.network.getMetadataStoreSchema(),
						context.targetSchemaNameStr);
				context.metadataTransformer.setParameter("networkAcronym", context.network.getAcronym());
				logInfo(context.network.getName() + " EntityRelation worker extraction on snapshot:"
						+ context.snapshotId + " :: STARTED");

				// --- Run Loop ---
				context.startTime = System.nanoTime(); // Pre Page 1

				for (RecordValidation record : context.recordsToProcess) {
					context.currentRecordIndex += 1;
					processItem(record, context); // Process Item

					if (context.currentRecordIndex % context.pageSize == 0) {
						logger.debug("Entity extraction progress " + context.network.getAcronym() + "::"
								+ context.targetSchemaNameStr
								+ " :: " + getCompletionRate(context)
								+ " (" + context.currentRecordIndex + " / " + context.totalRecords
								+ " records processed)");

						// Post Page equivalent
						context.endTime = System.nanoTime();
						long totalTime = context.endTime - context.startTime;
						logInfo("Extracting and persisting entities from " + context.pageSize
								+ " metadata records to db took: " + (totalTime / 1000000) + "ms");

						// Pre Page equivalent
						context.startTime = System.nanoTime();
					}
				}

				// --- Post Run ---
				logInfo(context.network.getName() + " Now merge needs to be done in shell.");
				logInfo(context.network.getName() + " EntityRelation worker extraction on snapshot:"
						+ context.snapshotId + " :: FINISHED");

				long finalTime = System.nanoTime();
				long totalTime = finalTime - context.initialTime;
				logInfo(context.network.getName() + String.format(" Extracting took: %s secs", totalTime / 1000000000));

			} catch (Exception e) {
				logError("Error in entity extraction: " + e.getMessage());
				throw e;
			}

		} else {
			logError("There aren't any LGKSnapshot for the network: " + context.network.getAcronym());
			// Should we throw?
		}
	}

	private void processItem(RecordValidation record, ExtractionContext context) {

		try {
			// Using getMetadata method which might throw exceptions
			OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(),
					metadataStore.getMetadata(context.snapshotMetadata, record.getPublishedMetadataHash()));

			// Profiler usage adapted
			context.profiler = new Profiler(context.profileModeBool, "Record internalID: " + record.getRecordId() + " ")
					.start();

			// record parameters to transformer
			context.metadataTransformer.setParameter("fingerprint",
					context.snapshotMetadata.getNetwork().getAcronym() + "_" + record.getRecordId());
			context.metadataTransformer.setParameter("identifier", record.getIdentifier());

			Document recordMetadataDocument = metadata.getDOMDocument();
			Document entityDataDocument = context.metadataTransformer.transform(recordMetadataDocument);

			context.profiler.messure("RecordXML2EntityXML", false);

			if (context.debugModeBool) {
				logger.info(metadata.toString());
				logger.info(context.metadataTransformer.transformToString(metadata.getDOMDocument()));
			}

			erService.setProfiler(context.profiler);
			erService.parseAndPersistEntityRelationDataFromXMLDocument(entityDataDocument, false);

			context.profiler.report(logger);

		} catch (Exception e) {
			String msg = "Error processing record internalID: " + record.getRecordId() + " -- identifier: "
					+ record.getIdentifier() + " -- msg: " + e.getMessage();
			logError(msg, context);
		}
	}

	private String getCompletionRate(ExtractionContext context) {
		if (context.totalRecords == 0)
			return percentajeFormat.format(0.0);
		return percentajeFormat.format(context.currentRecordIndex.doubleValue() / context.totalRecords.doubleValue());
	}

	/******************* Auxiliares ********** */

	private void logError(String message) {
		logger.error(message);
	}

	private void logError(String message, ExtractionContext context) {
		logger.error(message);
		if (context != null && context.snapshotId != null)
			snapshotLogService.addEntry(context.snapshotId, "ERROR: " + message);
	}

	private void logInfo(String message) {
		logger.info(message);
	}

}
