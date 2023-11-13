
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

import org.lareferencia.core.entity.domain.EntityType;

import lombok.Getter;
import lombok.Setter;
import org.lareferencia.core.worker.IRunningContext;

import java.time.LocalDateTime;

public class EntityIndexingRunningContext implements IRunningContext {
	
	static final String ID_PREFIX = "ENTITY_INDEXING::";
	
	@Getter
	@Setter
	EntityType entityType;
	
	@Getter
	@Setter
	String indexingConfigFile;

	@Getter
	@Setter
	String provenanceSource;

	@Getter
	@Setter
	LocalDateTime lastUdate = null;
	
	
	@Getter
	@Setter
	String indexerBeanName;
	
	@Getter
	@Setter
	Boolean deleteMode = false;
	
	@Getter
	@Setter
	int pageSize = 1000;

	@Getter
	@Setter
	int fromPage = 0;

	public EntityIndexingRunningContext(String indexingConfigFile, String indexeBeanName) {
		super();
		this.indexingConfigFile = indexingConfigFile;
		this.indexerBeanName = indexeBeanName;
	}
	
	@Override
	public String getId() {
		if ( entityType != null )
			return ID_PREFIX  + (entityType != null ? entityType.getName() + "::" : "") + indexerBeanName;
		else
			return ID_PREFIX  + "-" +  indexerBeanName; 
	}
	
    @Override
    public String toString() {
    	if ( entityType != null )
    		return entityType.getName() + entityType != null ? "(id:"+ entityType.getId() : "" +" indexer:" + indexerBeanName + ")";
    	else
    		return "indexer:" + indexerBeanName + ")";

    }
	
}
