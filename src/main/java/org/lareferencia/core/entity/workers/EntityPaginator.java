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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.worker.IPaginator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import lombok.Getter;

public class EntityPaginator implements IPaginator<Entity> {
	
	private static Logger logger = LogManager.getLogger(EntityPaginator.class);
	
	private static final int DEFAULT_PAGE_SIZE = 1000;
		
	@Getter
	private int pageSize = DEFAULT_PAGE_SIZE;	
	
	EntityRepository entityRepository;
	EntityType entityType;
	String provenanceSource;
	
	private int totalPages = 0;

	private int actualPage = 1;

	@Override
	public int getStartingPage() { return actualPage; }
	
	Page<Entity> page = null;


	public EntityPaginator(EntityRepository repository, EntityType entityType) {
		actualPage = 1;
		this.entityType = entityType;
		this.entityRepository = repository;
		logger.debug( "Creating entity type paginator: " + entityType.getName()  );
		obtainPage();
	}
	
	public EntityPaginator(EntityRepository repository) {
		actualPage = 1;
		this.entityRepository = repository;
		logger.debug( "Creating entity type paginator: ALL Entitties :: " );
		obtainPage();
	}
	
	public EntityPaginator(EntityRepository repository, String provenanceSource ) {
		actualPage = 1;
		this.entityRepository = repository;
		this.provenanceSource = provenanceSource;
		logger.debug( "Creating entity type paginator:: Entities from proveance source :: " + provenanceSource );
		obtainPage();
	}

	public EntityPaginator(EntityRepository repository, EntityType entityType, String provenanceSource ) {
		actualPage = 1;
		this.entityRepository = repository;
		this.entityType = entityType;
		this.provenanceSource = provenanceSource;
		logger.debug( "Creating entity type paginator:: Entities of type " + entityType.getName() + " from proveance source :: " + provenanceSource );
		obtainPage();
	}
	
	@Override
	public void setPageSize(int newsize) {
		
		if ( newsize != this.pageSize ) {
			this.pageSize = newsize;
			actualPage = 1;
			obtainPage();
		}
	}

	public void setActualPage(int actualPage) {
		this.actualPage = actualPage;
		obtainPage();
	}
	
	private Page<Entity> obtainPage() {
					
		page = null;
		Pageable pageable = PageRequest.of(actualPage-1, pageSize);
		
		if ( entityType != null )
			if ( provenanceSource != null )
				page = entityRepository.findDistinctEntityByDirtyAndEntityTypeIdAndSourceEntities_Provenance_Source(false, entityType.getId(), provenanceSource, pageable);
			else
				page = entityRepository.findDistinctEntityByDirtyAndEntityType(false, entityType, pageable);
		else
			if ( provenanceSource != null )
				page = entityRepository.findDistinctEntityByDirtyAndSourceEntities_Provenance_Source(false, provenanceSource, pageable);
			else
				page = entityRepository.findAll(pageable);
		
		this.totalPages = page.getTotalPages();
		return page;
		
	}
	

	public int getTotalPages() {
		return totalPages;
	}

	public Page<Entity> nextPage() { 
		
		if (actualPage <= totalPages) {			
			Page<Entity> page = obtainPage();
			actualPage++;
			return page;
		} else {
			logger.error("Page number is greater than total pages");
			return Page.empty();
		}
	}

}
