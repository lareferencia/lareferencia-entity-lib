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
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.EntityIndexingService;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityIndexingStats;
import org.lareferencia.core.entity.services.EntityLoadingMonitorService;
import org.lareferencia.core.util.Profiler;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.entity.workers.EntityIndexingRunningContext;
import org.lareferencia.core.entity.workers.EntityPaginator;
import org.springframework.beans.factory.annotation.Autowired;

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

	public EntityIndexingWorker() {
		super();
	}



	@Override
	public void preRun() {
		
		try { 

			if ( runningContext.getEntityType() != null )
				entityPaginator = new EntityPaginator(entityRepository, runningContext.getEntityType() );
			else 
				entityPaginator = new EntityPaginator(entityRepository );
			
			// set page size
			entityPaginator.setPageSize(runningContext.getPageSize());
			entityPaginator.setActualPage(runningContext.getFromPage());
			
			this.setPaginator(entityPaginator);			
			indexer = indexingService.getIndexer(runningContext.getIndexingConfigFile(), runningContext.getIndexerBeanName());
			
		} catch (Exception e) {
			logError("Error in Entity Relation Indexing: " + runningContext.toString() + " :: " + e.getMessage());
			error();
		}
	}


	@Override
	public void prePage() {
		
		profiler = new Profiler(enableProfiling, "").start();
		emptyPage = true;
		
	}

	@Override
	public void processItem(Entity entity) {


		try {

			// Delete or index depending
			if (runningContext.getDeleteMode())
				indexer.delete(entity.getId().toString());
			else {

				indexer.index(entity);
				entityMonitorService.addIndexedEntity(entity.getId(), entity.getEntityTypeId());

			}

			emptyPage = false;

		} catch (Exception e) {

			entityMonitorService.addIndexedEntityError(entity.getId(), e.getMessage());
			String msg = "Error indexing entity internal EntityTypeID: " + entity.getId() + " " + runningContext.toString() + " -- msg: " + e.getMessage();
			logError(msg);
		}


		
	}

	@Override
	public void postPage() {
		
		// execute only if at least one entity was indexed
		if (!emptyPage) {
			try {
				indexer.flush();
			} catch (EntityIndexingException e) {
				logError( "Error indexing page: " + this.getActualPage() + " ::" + e.getMessage() );
			}
		}
		
		profiler.messure("Page Indexing Time (ms): ", false);
		profiler.report(logger);
	}

	@Override
	public void postRun() {
		logInfo("EntityRelationIndexing worker :: FINISHED :: " + runningContext.toString());	
	}
	
	@Override
	public String toString() {
		return  "ERIndexer::" + "[" + percentajeFormat.format(this.getCompletionRate()) + "]"; 
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
