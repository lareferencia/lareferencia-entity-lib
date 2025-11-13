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
import org.lareferencia.core.repository.parquet.RecordValidation;
import org.lareferencia.core.repository.parquet.ValidationStatParquetRepository;
import org.lareferencia.core.service.management.SnapshotLogService;
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
import org.lareferencia.core.worker.BaseWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

import lombok.Getter;
import lombok.Setter;

public class EntityExtractionWorker extends BaseWorker<NetworkRunningContext> {

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
	
	private Long snapshotId;
	private SnapshotMetadata snapshotMetadata;
	List<RecordValidation> recordsToProcess;
	Integer currentRecordIndex = 0;
	Integer totalRecords = 0;
	Integer pageSize = 1000;

	NumberFormat percentajeFormat = NumberFormat.getPercentInstance();

	private IMDFormatTransformer metadataTransformer;
	
	@Autowired
	MDFormatTransformerService trfService;
	
	@Getter @Setter
	private String targetSchemaName;

	@Getter @Setter
	private boolean debugMode = false;
	
	@Getter @Setter
	private boolean profileMode = false;
	
	@Getter @Setter
	private Integer entityCacheSize = null;
	
//	@Autowired
//	EntityLRUCache entityCache;
//	
	private Profiler profiler;


	private long initialTime;
	private long startTime;
	private long endTime;
	

	public EntityExtractionWorker() {
		super();
	}

	public void preRun() {
		
		initialTime = System.nanoTime();
	
		// busca el lgk
		snapshotId = snapshotStore.findLastGoodKnownSnapshot(runningContext.getNetwork());

		if ( snapshotId != null ) { // solo si existe un lgk

			snapshotMetadata = snapshotStore.getSnapshotMetadata(snapshotId);

			logger.debug("Full entity extraction on snapshot: " + snapshotId);
			logInfo("Full entity extraction: "+ runningContext.toString() +" (" + this.targetSchemaName + ")");

			// Obtiene los registros válidos desde Parquet
			try {
				recordsToProcess = parquetRepository.getRecordValidationListBySnapshotAndStatus(snapshotId, RecordStatus.VALID);	
				totalRecords = recordsToProcess.size();

				// establece el transformador para extracción de entidades
				metadataTransformer = trfService.getMDTransformer(runningContext.getNetwork().getMetadataStoreSchema(), targetSchemaName);
				metadataTransformer.setParameter("networkAcronym", runningContext.getNetwork().getAcronym() );
				logInfo(runningContext.toString() + " EntityRelation worker extraction on snapshot:" + snapshotId + " :: STARTED");

			} catch (MDFormatTranformationException e) {
				logError( runningContext.toString() + " EntityExtraction Worker :: Error on loading metadata transformer services : " + runningContext.getNetwork().getMetadataStoreSchema() + "2" + targetSchemaName  + " :: CANCELLED");
				error();
			} catch (IOException e) {
				logError("I/O ERROR at entity extraction: " + runningContext.toString() + " " 
						+ runningContext.getNetwork().getMetadataStoreSchema() + " >> " + targetSchemaName 
						+ " error: " + e.getMessage());
				error();
			}

		} else {

			logError( "There aren't any LGKSnapshot for the network: " + runningContext.toString() + " :: CANCELLED" );
			error();
		}
		
		// cache setting
//		if ( entityCacheSize != null && entityCacheSize > 0) {
//			logInfo(runningContext.toString() + " Creating entity cache ...");	
//			this.entityCache.setCapacity(entityCacheSize);
//			this.erService.setEntityCache(this.entityCache);
//		}
	}

	@Override
	public void run() {

		preRun();

		if (currentRecordIndex == 0)
			prePage();

		recordsToProcess.forEach( record -> {
			currentRecordIndex += 1;
			processItem(record);

			if ( currentRecordIndex % pageSize == 0 ) {
				logger.debug("Entity extraction progress " + runningContext.getNetwork().getAcronym() + "::" + this.targetSchemaName 
						+ " :: " + percentajeFormat.format(this.getCompletionRate()) 
						+ " (" + currentRecordIndex + " / " + totalRecords + " records processed)" );

				postPage();
				prePage();
			}
		});

		postRun();
	}


	public void prePage() {
		
		startTime = System.nanoTime();

	}

	public void processItem(RecordValidation record) {
		
		try {
			
			OAIRecordMetadata metadata = new OAIRecordMetadata( record.getIdentifier(), 
				metadataStore.getMetadata(snapshotMetadata, record.getPublishedMetadataHash()) ); 
			
			profiler = new Profiler(profileMode, "Record internalID: " + record.getRecordId() + " ").start();
			
			// record parameters to transformer
			metadataTransformer.setParameter("fingerprint", snapshotMetadata.getNetwork().getAcronym() + "_" + record.getRecordId() );
			metadataTransformer.setParameter("identifier", record.getIdentifier());
			// Note: timestamp not available in RecordValidation, using current time or omitting
			
			Document recordMetadataDocument = metadata.getDOMDocument();
			Document entityDataDocument =  metadataTransformer.transform(recordMetadataDocument);
			
			profiler.messure("RecordXML2EntityXML", false);
					
			if ( debugMode ) {
				logger.info( metadata.toString() );
				logger.info( metadataTransformer.transformToString(metadata.getDOMDocument()) );
			}
			
			erService.setProfiler(profiler);
			erService.parseAndPersistEntityRelationDataFromXMLDocument(entityDataDocument, false);
			
			profiler.report(logger);
			
			
		} catch (OAIRecordMetadataParseException e) {
			String msg = "Error parsing metadata for record internalID: " + record.getRecordId() + " -- identifier: " +   record.getIdentifier() + " -- msg: " + e.getMessage() ;
			logError(msg);
		} catch (MetadataRecordStoreException e) {
			String msg = "Error retrieving metadata for record internalID: " + record.getRecordId() + " -- identifier: " +   record.getIdentifier() + " -- msg: " + e.getMessage() ;
			logError(msg);
		} catch (Exception e) {
			String msg = "Error processing record internalID: " + record.getRecordId() + " -- identifier: " +   record.getIdentifier() + " -- msg: " + e.getMessage() ;
			logError(msg);
		}
		
	}


	public void postPage() {
		
		endTime   = System.nanoTime();
		
		long totalTime = endTime - startTime;
		logInfo( runningContext.toString() + String.format(" Extracting and persisting entities from %s metadata records to db took: %sms", pageSize, totalTime/1000000) );

	}

	public void postRun() {
		
//		if ( entityCacheSize != null && entityCacheSize > 0) {
//			logInfo(runningContext.toString() + " Persisting entity cache ...");	
//			entityCache.syncAndClose();
//		}
		
		logInfo(runningContext.toString() + " Now merge needs to be done in shell.");
		//erService.mergeEntityRelationData();
		
		logInfo(runningContext.toString() + " EntityRelation worker extraction on snapshot:" + snapshotId + " :: FINISHED - execute the merge action in shell");
		
		
		long finalTime = System.nanoTime();  
		long totalTime = finalTime - initialTime;
		logInfo( runningContext.toString() + String.format(" Extracting took: %s secs", totalTime/1000000000) );

		
	}
	
	@Override
	public String toString() {
		return  "EntityMapper[" + percentajeFormat.format(this.getCompletionRate()) + "]"; 
	}	

	Double getCompletionRate() {
		if ( totalRecords == 0 )
			return 0.0;
		else
			return ( currentRecordIndex.doubleValue() / totalRecords.doubleValue() );
	}	

	/******************* Auxiliares ********** */
	private void error() {
		this.stop();
	}

	private void logError(String message) {
		logger.error(message);
		snapshotLogService.addEntry(snapshotId, "ERROR: " + message);		
	}

	private void logInfo(String message) {
		logger.info(message);
		snapshotLogService.addEntry(snapshotId, "INFO: " + message);		
	}

}
