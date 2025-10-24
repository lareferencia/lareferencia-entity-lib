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

package org.lareferencia.core.entity.indexing.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.resource.beans.container.internal.NoSuchBeanException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class EntityIndexingService  {
	
	private static Logger logger = LogManager.getLogger(EntityIndexingService.class);

//	@Getter
//	@Setter
//	@Value("${entity.indexing.configfile}")
//	private String indexingConfigFilename;
//	
//	private IndexingConfiguration config;
		
	@Autowired
	ApplicationContext applicationContext;
	
//	@Getter
//	Map<String, Map<String, EntityIndexingConfig>> indicesByNameAndEntityType;
//	
//	@Getter
//	Map<String, IndexConfig> indicesByName;
	
	public EntityIndexingService() {
		super();
	}
	
//	@PostConstruct
//	private void reloadConfig() {
//		try {
//			
//			config = IndexingConfiguration.loadFromXml(indexingConfigFilename); 
//			
//			indicesByNameAndEntityType = new HashMap<String, Map<String,EntityIndexingConfig>>();
//			indicesByName = new HashMap<String, IndexConfig>();
//			
//			for (IndexConfig index: config.getIndices() ) {
//				
//				indicesByNameAndEntityType.put(index.getName(), new HashMap<String, EntityIndexingConfig>());
//				indicesByName.put(index.getName(), index);
//				
//				for ( EntityIndexingConfig entity: index.getEntityIndices() ) 
//					indicesByNameAndEntityType.get( index.getName() ).put( entity.getEntityType(), entity );
//					
//			}
//			
//			logger.info("Entity Service Started - config " + indexingConfigFilename + " loaded");
//			
//			
//		} catch (Exception e) {
//			logger.error("Error loading entity indexing config: " + e.getMessage());
//		}		
//	}
	
	public IEntityIndexer getIndexer(String configFilePath, String indexerBeanName) throws EntityIndexingException  {
		
		
	  try {
			
				IEntityIndexer indexer = (IEntityIndexer) applicationContext.getBean( indexerBeanName );
				indexer.setConfig( configFilePath );
				
				return indexer;
			
			} catch (NoSuchBeanException e) {
				throw new EntityIndexingException("EntityIndexer Bean:" + indexerBeanName + " is not registed in spring context.");
			} catch (Exception e) {
				throw new EntityIndexingException("EntityIndexer Bean setup error:" + e.getMessage());
			} 
		
	}
	
	
	public IEntityIndexer getIndexer(String indexerBeanName) throws EntityIndexingException  {
		
		
		  try {
				
					IEntityIndexer indexer = (IEntityIndexer) applicationContext.getBean( indexerBeanName );
					return indexer;
				
				} catch (NoSuchBeanException e) {
					throw new EntityIndexingException("EntityIndexer Bean:" + indexerBeanName + " is not registed in spring context.");
				} catch (Exception e) {
					throw new EntityIndexingException("EntityIndexer Bean setup error:" + e.getMessage());
				} 
			
		}
		
		
		
}
		

//		if ( indicesByNameAndEntityType.get(indexName) != null && indicesByNameAndEntityType.get(indexName).get(entityTypeName) != null ) {
//			
//			
//			IndexConfig index = indicesByName.get(indexName);
//			
//			try {
//			
//				IEntityIndexer indexer = (IEntityIndexer) applicationContext.getBean( index.getIndexer() );
//				
//				indexer.setEntityType(entityTypeName);
//				indexer.setIndexName(indexName);
//				indexer.setConfig( indicesByNameAndEntityType.get(indexName).get(entityTypeName) );
//				
//				return indexer;
//			
//			} catch (NoSuchBeanException e) {
//				throw new EntityIndexingException("EntityIndexer Bean:" + index.getIndexer() + " is not registed in spring context.");
//			} catch (EntityRelationException e) {
//				throw new EntityIndexingException("EntityIndexer Bean setup error:" + e.getMessage());
//			} 
//			
//		} else
//			throw new EntityIndexingException("Index:" + indexName + " for EntityType:" + entityTypeName + " is not defined in indexing configuration.");
//		
//	}
//	
	

