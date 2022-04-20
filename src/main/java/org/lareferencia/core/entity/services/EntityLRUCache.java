
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

package org.lareferencia.core.entity.services;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component
@Scope(value = "prototype")
public class EntityLRUCache {

	private Map<String, UUID> entityIdBySemanticId = new Hashtable<String, UUID>();
	
	private LinkedHashMap<UUID, Entity> lruCacheMap;
    private int capacity;
    private final boolean SORT_BY_ACCESS = true;
    private final float LOAD_FACTOR = 0.75F;
    
    private final int DEFAULT_CAPACITY = 1000;
    
    @Autowired
	EntityRepository entityRepository;
    
    
    public EntityLRUCache(){
        this.capacity = DEFAULT_CAPACITY;
        this.lruCacheMap = new LinkedHashMap<>(capacity, LOAD_FACTOR, SORT_BY_ACCESS);
        entityIdBySemanticId = new Hashtable<String, UUID>();
    }
    
    public void setCapacity(int capacity) {
    	this.capacity = capacity;
        this.lruCacheMap = new LinkedHashMap<>(capacity, LOAD_FACTOR, SORT_BY_ACCESS);
        this.entityIdBySemanticId = new Hashtable<String, UUID>();

    }

    public Entity get(String semanticId){
    	
    	UUID entityId = entityIdBySemanticId.get(semanticId);
    	
    	if (entityId == null)
    		return null;
    	
        return lruCacheMap.get(entityId);
    }
    
    public Entity get(List<String> semanticIds){
    	
    	UUID entityId = null;
    	
    	for (String semanticId: semanticIds) {
    		entityId = entityIdBySemanticId.get(semanticId);
    	}
    	
    	if (entityId == null)
    		return null;
    	
        return lruCacheMap.get(entityId);
    }

    public void put(Entity entity){
    	
        if(lruCacheMap.containsKey(entity.getId())){
        
        	lruCacheMap.remove(entity.getId());
        
        } else if(lruCacheMap.size() >= capacity){
        	
        	UUID toBeRemovedKey = lruCacheMap.keySet().iterator().next();
        	Entity toBeRemovedEntity = lruCacheMap.get(toBeRemovedKey);
        	
        	for (SemanticIdentifier semanticId : toBeRemovedEntity.getSemanticIdentifiers() )
        		entityIdBySemanticId.remove(semanticId.getIdentifier());
        	
            lruCacheMap.remove(toBeRemovedKey);
            
            // if is dirty then save evicted entity
            //if ( toBeRemovedEntity.isDirty() )
            	entityRepository.save(toBeRemovedEntity);
            
        }
        
        for (SemanticIdentifier semanticId : entity.getSemanticIdentifiers() )
    		entityIdBySemanticId.put(semanticId.getIdentifier(), entity.getId());
        
        lruCacheMap.put(entity.getId(), entity);
    }
    
    public void syncAndClose() {
    	
    	for (Entity entity: lruCacheMap.values() )
    		//if (entity.isDirty())
    			this.entityRepository.save(entity);
    	
    }

}
