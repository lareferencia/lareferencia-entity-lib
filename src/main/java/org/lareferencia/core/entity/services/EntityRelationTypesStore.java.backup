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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.RelationTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;



@Service
@Scope(value = "prototype")
public class EntityRelationTypesStore {
	
	private static final Logger logger = LogManager.getLogger(EntityRelationTypesStore.class);
	
	@Autowired
	EntityTypeRepository entityTypeRepository;
	
	@Autowired
	RelationTypeRepository relationTypeRepository;
	
	
	private final Map<String, EntityType> entityTypeByName = new HashMap<String, EntityType>();
	private final Map<Long, EntityType> entityTypeById = new HashMap<Long, EntityType>();

	private final Map<String, RelationType> relationTypeByName = new HashMap<String, RelationType>();
	private final Map<Long, RelationType> relationTypeById = new HashMap<Long, RelationType>();

	
	public EntityRelationTypesStore() {
		
	}
	

	public EntityType getEntityTypeFromName(String name) throws EntityRelationException {
		
		EntityType type = entityTypeByName.get(name);
		
		// if exists in map return cached value
		if ( type != null )
			return type;
		
		Optional<EntityType> optionalType = entityTypeRepository.findOneByName(name);
		
		if ( optionalType.isPresent() ) {
			type = optionalType.get();
			entityTypeByName.put(name, type); // put type un map
			entityTypeById.put(type.getId(), type); // put also by id
			return type;
		}
		else {
			logger.error("Type: " + name + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + name + " does not exists in metamodel" );
		}
	}
	

	public RelationType getRelationTypeFromName(String name) throws EntityRelationException {
		
		RelationType type = relationTypeByName.get(name);
		
		// if exists in map return cached value
		if ( type != null )
			return type;
		
		Optional<RelationType> optionalType = relationTypeRepository.findOneByName(name);
		
		if ( optionalType.isPresent() ) {
			type = optionalType.get();
			relationTypeByName.put(name, type); // put type un map
			relationTypeById.put(type.getId(), type); // put also by id
			return type;
		}
		else {
			logger.error("Type: " + name + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + name + " does not exists in metamodel" );
		}
	}
	
	public EntityType getEntityTypeFromId(Long id) throws EntityRelationException {
		
		EntityType type = entityTypeById.get(id);
		
		// if exists in map return cached value
		if ( type != null )
			return type;
		
		Optional<EntityType> optionalType = entityTypeRepository.findById(id);
		
		if ( optionalType.isPresent() ) {
			type = optionalType.get();
			entityTypeByName.put(type.getName(), type); // put type un map
			entityTypeById.put(type.getId(), type); // put also by id
			return type;
		}
		else {
			logger.error("Type: " + id + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + id + " does not exists in metamodel" );
		}
	}
	
	
	public RelationType getRelationTypeFromId(Long id) throws EntityRelationException {
		
		RelationType type = relationTypeById.get(id);
		
		// if exists in map return cached value
		if ( type != null )
			return type;
		
		Optional<RelationType> optionalType = relationTypeRepository.findById(id);
		
		if ( optionalType.isPresent() ) {
			type = optionalType.get();
			relationTypeByName.put(type.getName(), type); // put type un map
			relationTypeById.put(type.getId(), type); // put also by id
			return type;
		}
		else {
			logger.error("Type: " + id + " does not exists in metamodel");
			throw new EntityRelationException("Type: " + id + " does not exists in metamodel" );
		}
	}

}
