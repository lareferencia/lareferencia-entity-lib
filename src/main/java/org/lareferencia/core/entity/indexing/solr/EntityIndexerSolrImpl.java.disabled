
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

package org.lareferencia.core.entity.indexing.solr;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.indexing.service.AbstractPlainEntityIndexer;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.IEntity;
import org.lareferencia.core.entity.repositories.solr.EntitySolrRepository;
import org.springframework.beans.factory.annotation.Autowired;

public class EntityIndexerSolrImpl extends AbstractPlainEntityIndexer  {
	
	private static Logger logger = LogManager.getLogger(EntityIndexerSolrImpl.class);	
	
	List<EntitySolr> entityBuffer = new LinkedList<EntitySolr>(); 

	@Autowired
	EntitySolrRepository entitySolrRepository;

	@Override
	protected IEntity createIEntity(String id, String type) {
		return new EntitySolr(id,type);
	}

	@Override
	public void delete(String id) {
		logger.debug("Deleting entity: " + id );
		this.entitySolrRepository.deleteById(id);
	}
	

	@Override
	protected void saveIEntity(IEntity ientity) {
		entityBuffer.add( (EntitySolr) ientity  );
	}
	
	@Override
	public void flush() {
		entitySolrRepository.saveAll( entityBuffer);
		entityBuffer = new LinkedList<EntitySolr>(); 
	}

	@Override
	public void prePage() throws EntityIndexingException {
		// TODO Auto-generated method stub
	}


	

}
