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

import java.text.NumberFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.backend.services.SnapshotLogService;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityLRUCache;
import org.lareferencia.core.metadata.IMDFormatTransformer;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.MDFormatTranformationException;
import org.lareferencia.core.metadata.MDFormatTransformerService;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.util.IRecordFingerprintHelper;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.util.date.DateHelper;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.IPaginator;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

import lombok.Getter;
import lombok.Setter;

public class EntityExtractionWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

	private static Logger logger = LogManager.getLogger(EntityExtractionWorker.class);

	@Autowired
	private SnapshotLogService snapshotLogService;

	@Autowired
	EntityDataService erService;
	
	@Autowired
	private IMetadataRecordStoreService metadataStoreService;
	
	private Long snapshotId;

	NumberFormat percentajeFormat = NumberFormat.getPercentInstance();
	
	@Autowired
	private IRecordFingerprintHelper fingerprintHelper;

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

	@Override
	public void preRun() {
		
			initialTime = System.nanoTime();
		
			// busca el lgk
			snapshotId = metadataStoreService.findLastGoodKnownSnapshot(runningContext.getNetwork()); // snpshotRepository.findLastGoodKnowByNetworkID(runningContext.getNetwork().getId());

			if ( snapshotId != null ) { // solo si existe un lgk

				// establece una paginator para recorrer los registros que no sean inválidos
				IPaginator<OAIRecord> paginator = metadataStoreService.getValidRecordsPaginator(snapshotId);
				paginator.setPageSize(this.getPageSize());
				this.setPaginator(paginator);
				
				
				// establece el transformador para indexación
				try {
					metadataTransformer = trfService.getMDTransformer(runningContext.getNetwork().getMetadataStoreSchema(), targetSchemaName);
					metadataTransformer.setParameter("networkAcronym", runningContext.getNetwork().getAcronym() );
					logInfo(runningContext.toString() + " EntityRelation worker extraction on snapshot:" + snapshotId + " :: STARTED");

				} catch (MDFormatTranformationException e) {
					logError( runningContext.toString() + " EntityExtraction Worker :: Error on loading metadata transformer services : " + runningContext.getNetwork().getMetadataStoreSchema() + "2" + targetSchemaName  + " :: CANCELLED");
					this.setPaginator( null );
					error();
				}
				
				
								
			} else {

				logError( "There aren't any LGKSnapshot for the network: " + runningContext.toString() + " :: CANCELLED" );
				this.setPaginator( null );
				error();
			}
			
			
			// cache setting
//			if ( entityCacheSize != null && entityCacheSize > 0) {
//				logInfo(runningContext.toString() + " Creating entity cache ...");	
//				this.entityCache.setCapacity(entityCacheSize);
//				this.erService.setEntityCache(this.entityCache);
//			}
			
			

			
	}


	@Override
	public void prePage() {
		
		startTime = System.nanoTime();

	}

	@Override
	public void processItem(OAIRecord record) {
		
		try {
			
			OAIRecordMetadata metadata = metadataStoreService.getPublishedMetadata(record);
			
			profiler = new Profiler(profileMode, "Record internalID: " + record.getId() + " ").start();
			
			// record parameters to transformer
			metadataTransformer.setParameter("fingerprint", fingerprintHelper.getFingerprint(record) );
			metadataTransformer.setParameter("identifier", record.getIdentifier());
			metadataTransformer.setParameter("timestamp", DateHelper.getDateTimeMachineString(record.getDatestamp()) );
			
			Document recordMetadataDocument = metadata.getDOMDocument();
			Document entityDataDocument =  metadataTransformer.transform(recordMetadataDocument);
			
			profiler.messure("RecordXML2EntityXML", false);
					
			if ( debugMode ) {
				logger.info( metadata.toString() );
				logger.info( metadataTransformer.transformToString(metadata.getDOMDocument()) );
			}
			
			erService.setProfiler(profiler);
			erService.parseAndPersistEntityRelationDataFromXMLDocument(entityDataDocument);
			
			profiler.report(logger);
			
			
		} catch (Exception e) {
			
			String msg = "Error processing record internalID: " + record.getId() + " -- identifier: " +   record.getIdentifier() + " -- msg: " + e.getMessage() ;
			//e.printStackTrace();
			logError(msg);
		}
		
	}


	@Override
	public void postPage() {
		
		endTime   = System.nanoTime();
		
		long totalTime = endTime - startTime;
		logInfo( runningContext.toString() + String.format(" Extracting and persisting entities from %s metadata records to db took: %sms", this.getPageSize(),totalTime/1000000) );

	}

	@Override
	public void postRun() {
		
//		if ( entityCacheSize != null && entityCacheSize > 0) {
//			logInfo(runningContext.toString() + " Persisting entity cache ...");	
//			entityCache.syncAndClose();
//		}
		
		logInfo(runningContext.toString() + " Updating Entities ...");
		erService.mergeEntityRelationData();
		
		logInfo(runningContext.toString() + " EntityRelation worker extraction on snapshot:" + snapshotId + " :: FINISHED");
		
		
		long finalTime = System.nanoTime();  
		long totalTime = finalTime - initialTime;
		logInfo( runningContext.toString() + String.format(" Extracting took: %s secs", totalTime/1000000000) );

		
	}
	
	@Override
	public String toString() {
		return  "EntityMapper[" + percentajeFormat.format(this.getCompletionRate()) + "]"; 
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
