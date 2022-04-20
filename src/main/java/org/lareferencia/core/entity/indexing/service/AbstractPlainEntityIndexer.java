
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.indexing.plain.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.plain.config.FieldIndexingConfig;
import org.lareferencia.core.entity.indexing.plain.config.IndexConfig;
import org.lareferencia.core.entity.indexing.plain.config.IndexingConfiguration;
import org.lareferencia.core.entity.services.EntityDataService;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public abstract class AbstractPlainEntityIndexer implements IEntityIndexer {

	
	private static Logger logger = LogManager.getLogger(AbstractPlainEntityIndexer.class);	
	
	@Autowired
	EntityDataService entityDataService;
	
	Map<String,  EntityIndexingConfig> configsByEntityType;
	
	private IndexingConfiguration indexingConfiguration;
	private String indexingConfigFilename;
	
	@Override
	public void setConfig(String indexingConfigFilename) {
		
		this.indexingConfigFilename = indexingConfigFilename;
		
		try {
			
			indexingConfiguration = IndexingConfiguration.loadFromXml(indexingConfigFilename); 
			
			configsByEntityType = new HashMap<String, EntityIndexingConfig>();
			
			IndexConfig index = indexingConfiguration.getIndices().get(0);
				
			for ( EntityIndexingConfig entityIndexingConfig: index.getEntityIndices() ) 
				configsByEntityType.put( entityIndexingConfig.getEntityType(), entityIndexingConfig );
				
			logger.info("Plain Indexer Config File: " + indexingConfigFilename + " loaded");			
			
		} catch (Exception e) {
			logger.error("Plain Indexer Config File: " + indexingConfigFilename + " failed - " + e.getMessage());
		}		
	}
	

	@Override
	public void index(Entity entity) throws EntityIndexingException {
		
		try {
			
			EntityType type = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());
			
			EntityIndexingConfig entityIndexingConfig = configsByEntityType.get( type.getName() );
			if ( entityIndexingConfig == null )
				throw new EntityIndexingException("Error indexing entity: " + entity.getId() + " " + this.indexingConfigFilename + " doesn´t contains a indexing config for " + type.getName() + " EntityType");
			
			
			IEntity ientity = createIEntity(entity.getId().toString(), type.getName());
			
			Multimap<String, Relation> relationsMap = this.getRelationMultimap(entity);
			
			
			for (SemanticIdentifier semanticId: entity.getSemanticIdentifiers() )
				ientity.addSemanticId(semanticId.getIdentifier());
			
			
//			for (Provenance provenance: entity.getProvenances() )
//				ientity.addProvenanceId(provenance.getProvenanceStr());
//			
		
			for (FieldIndexingConfig fieldConfig: entityIndexingConfig.getIndexFields() ) {	
				processFieldConfig(entity, relationsMap, fieldConfig, ientity);
			}
			
			for (FieldIndexingConfig identifierConfig: entityIndexingConfig.getIndexRelatedIds() ) {	
				processIndentifierConfig(entity, relationsMap, identifierConfig, ientity);
			}
			
			saveIEntity(ientity);

		
		} catch (EntityRelationException e) {
			throw new EntityIndexingException("Indexing error. " + e.getMessage());
		}
		
	
	}
	
	
	protected abstract IEntity createIEntity(String id, String type);
	protected abstract void saveIEntity(IEntity entity);
	
	
	private void processIndentifierConfig(Entity entity, Multimap<String, Relation> relationsMap, FieldIndexingConfig config, IEntity ientity) throws EntityRelationException {

		if ( config.getName() != null && config.getSourceRelation() != null && config.getSourceMember() !=null ) {
			for (Relation relation :  relationsMap.get(config.getSourceRelation()) ) 
				ientity.addRelatedIdentifier(config.getName(), relation.getRelatedEntity(entity.getId()).getId().toString() );
		}
	}

	private void processFieldConfig(Entity entity,  Multimap<String, Relation> relationsMap, FieldIndexingConfig config, IEntity ientity) throws EntityIndexingException {
	
		String sourceFieldName = config.getSourceField();
		
		
		if ( sourceFieldName == null )
			throw new EntityIndexingException("Error Indexing Entity Field " + config.getName() + " source field is not defined" );
			
		
		if ( config.getSourceRelation() != null ) {// is a relation indexing
						
			for (Relation relation : relationsMap.get(config.getSourceRelation()) ) {
			
				if ( config.getSourceMember() != null ) { // is a related entity field
					
					Entity relatedEntity = relation.getRelatedEntity(entity.getId());
					
					// add all occrs of the given source field from all entities of type source member related by source relation 
					processFieldOccurrence( relatedEntity.getFieldOccurrences(sourceFieldName) , config, ientity);
						
					
				} else  {// is a relation attribute 		
											
					processFieldOccurrence( relation.getFieldOccurrences(sourceFieldName), config, ientity);
				}
			}
			
		} else  {// is a entity field	
			processFieldOccurrence( entity.getFieldOccurrences(sourceFieldName) , config, ientity);
		}
	}	
	
	
	
    private void processFieldOccurrence(Collection<FieldOccurrence> occurrences, FieldIndexingConfig config,
            IEntity ientity) {

        for (FieldOccurrence occr : occurrences)
            try {
                
                EntityFieldValue fieldValue;

                if (config.getSourceSubfield() != null) {
                    fieldValue = new EntityFieldValue.Builder().fromComplexFieldOccurrence(occr, config.getSourceSubfield()).build();
                } else {
                    fieldValue = new EntityFieldValue.Builder().fromFieldOccurrence(occr).build();
                }

                // Add field occurrences based on field type
                switch (config.getType()) {
                case DATE:
                    ientity.addDateFieldOccurrence(config.getName(), fieldValue, null);
                    ientity.addFieldOccurrence(config.getName(), fieldValue);
                    break;
                case TEXT:
                    ientity.addFieldOccurrence(config.getName(), fieldValue);
                    break;
                case KEYWORD:
                    ientity.addFieldOccurrence(config.getName(), fieldValue);
                    break;
                case NUMBER:
                    //TODO add support
                    break;
                case STRING:
                    ientity.addFieldOccurrence(config.getName(), fieldValue);
                    break;
                default:
                    ientity.addFieldOccurrence(config.getName(), fieldValue);
                    break;
                }

                if (config.getSortable())
                    ientity.addSortingFieldOccurrence(config.getName(), fieldValue);

            } catch (EntityRelationException e) {
                logger.error("Error indexing field: " + config.getSourceField() + " subfield: "
                        + config.getSourceSubfield() + "::" + e.getMessage());
            }

    }

	
	@Override
	public void deleteAll(Collection<String> entityIdList) throws EntityIndexingException {
		logger.debug("Deleting  " + entityIdList.size() + " entities" );
		for (String entityId: entityIdList)
			delete(entityId);
	}
	
	private Multimap<String, Relation> getRelationMultimap(Entity entity) throws EntityRelationException {
		
		Multimap<String, Relation> relationsByName = ArrayListMultimap.create();
		
		
		for (Relation relation: entity.getFromRelations() ) {
			RelationType rtype = entityDataService.getRelationTypeFromId(relation.getRelationTypeId());			
			relationsByName.put(rtype.getName(), relation);
		}
		
		for (Relation relation: entity.getToRelations() ) {
			RelationType rtype = entityDataService.getRelationTypeFromId(relation.getRelationTypeId());			
			relationsByName.put(rtype.getName(), relation);
		}
				
		return relationsByName;
		
	}

}
