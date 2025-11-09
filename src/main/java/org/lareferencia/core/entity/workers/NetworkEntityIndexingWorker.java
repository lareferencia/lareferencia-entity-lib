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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.EntityIndexingService;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.RecordStatus;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.IPaginator;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.Getter;
import lombok.Setter;


public class NetworkEntityIndexingWorker extends BaseBatchWorker<String, NetworkRunningContext> {

	private static Logger logger = LogManager.getLogger(NetworkEntityIndexingWorker.class);
	
	@Getter
	@Setter
	Boolean enableProfiling = true;
	
	@Getter
	@Setter
	RecordStatus status = RecordStatus.VALID;
	
	@Getter
	@Setter
	String indexerBeanName = "entityIndexerSolr";
	
	@Getter
	@Setter
	String indexingConfigFilePath = "";
	
	@Getter
	@Setter
	List<String> entityTypeNameList = new ArrayList<String>();
	
	//List<EntityType> entityTypeList = new ArrayList<EntityType>();
	Set<Long> entityTypeIdSet = new HashSet<Long>();
	
	@Autowired
	EntityDataService erService;
	
	@Autowired
	EntityIndexingService indexingService;
	
	IPaginator<String> identifierPaginator;

	@Autowired
	IMetadataRecordStoreService metadataStoreService;
	
	IEntityIndexer indexer;

	private Profiler profiler;
	
	NumberFormat percentajeFormat = NumberFormat.getPercentInstance();
	
	String networkAcronym = "";

	boolean emptyPage = true;
	

	public NetworkEntityIndexingWorker() {
		super();
	}

	@Override
	public void preRun() {
		
		
		networkAcronym = runningContext.getNetwork().getAcronym();
		Long snapshotId = metadataStoreService.findLastGoodKnownSnapshot(runningContext.getNetwork()); 

		if (snapshotId != null) { // solo si existe un lgk

			identifierPaginator = metadataStoreService.getRecordIdentifiersPaginator(snapshotId, status);
			identifierPaginator.setPageSize(this.getPageSize());
			this.setPaginator(identifierPaginator);

		
		} else {

			logError("Didn't find LGKSnapshot on the network: " + runningContext.toString());
			this.setPaginator(null);
			error();
		}
		
		
		try {
			indexer = indexingService.getIndexer(this.indexingConfigFilePath, this.indexerBeanName);
		} catch (EntityIndexingException e) {
		
			logError("Error in Entity Relation Indexing Config/Indexer: " + runningContext.toString() + " :: " + this.indexingConfigFilePath + " :: " + this.indexerBeanName  + " :: "+ e.getMessage());		
		}
		
		
		
		// Load entity types
		for ( String entityTypeName : entityTypeNameList ) {
			
			try {
	
				entityTypeIdSet.add( erService.getEntityTypeFromName(entityTypeName).getId() );
				
			} catch (EntityRelationException e) {
				logError(runningContext.toString() + " :: EntityType :" + entityTypeName + " not found!!!");

			}
			
		}
		
	}


	@Override
	public void prePage() {
		
		profiler = new Profiler(enableProfiling, "").start();
		emptyPage = true;

	}

	@Override
	public void processItem(String oaiIdentifier) {
		
		// find all entities related with this provenace source and record
		List<Entity> entities = erService.findEntitiesByProvenanceSourceAndRecordId(networkAcronym, oaiIdentifier);
		
		
		for (Entity entity: entities) {
			
			try {
				
				// if this entity belongs to a listed type
				if ( entityTypeIdSet.contains( entity.getEntityTypeId() )) {
					indexer.index(entity);
					emptyPage = false;
				}
				
			} catch (EntityIndexingException e) {
			
				logError("Error indexing entity: " + entity.getId() + " " + e.getMessage());
			}	
		}
		
	
	}

	@Override
	public void postPage() {

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
		logInfo("NetworkEntityIndexing worker :: " +  runningContext.toString()  + ":: FINISHED");
		
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
		return  "NetworkEntityIndexing::" + runningContext.getNetwork().getAcronym()+"[" + percentajeFormat.format(this.getCompletionRate()) + "]"; 
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
