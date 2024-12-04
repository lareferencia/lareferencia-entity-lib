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

package org.lareferencia.core.entity.indexing.nested.config;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@XmlRootElement(name="indexed-entity")
@Setter
public class EntityIndexingConfig {
	
	private String entityType = null;
	private String entityRelation = null;

	private Boolean indexSemanticIds = false;
	@XmlAttribute(name="indexSemanticIds", required = false)
	public Boolean getIndexSemanticIds() {
		return indexSemanticIds;
	}

	private Boolean indexEntityType = false;
	@XmlAttribute(name="indexEntityType", required = false)
	public Boolean getindexEntityType() {
		return indexEntityType;
	}

	private List<FieldIndexingConfig> indexFields = new LinkedList<FieldIndexingConfig>();
	private List<EntityIndexingConfig> indexNestedEntities = new LinkedList<EntityIndexingConfig>();

	@XmlElementWrapper(name="index-fields")
	@XmlElement(name="index-field")
	public List<FieldIndexingConfig> getIndexFields() {
		return indexFields;
	}

	public void setIndexFields(List<FieldIndexingConfig> fields) {
		this.indexFields = new LinkedList<FieldIndexingConfig>();
		for (FieldIndexingConfig entityField : fields) {
			this.indexFields.add(entityField);
		}
	}
	
	@XmlElementWrapper(name="index-from-nested-entities")
	@XmlElement(name="indexed-entity")
	public List<EntityIndexingConfig> getIndexFromNestedEntities() {
		return indexNestedEntities;
	}

	@XmlElementWrapper(name="index-to-nested-entities")
	@XmlElement(name="indexed-entity")
	public List<EntityIndexingConfig> getIndexToNestedEntities() {
		return indexNestedEntities;
	}
	
	public void setIndexNestedEntities(List<EntityIndexingConfig> nestedEntities) {
		this.indexNestedEntities = new LinkedList<EntityIndexingConfig>();
		for (EntityIndexingConfig nestedEntity : nestedEntities) {
			this.indexNestedEntities.add(nestedEntity);
		}
	}

	@XmlAttribute(name="source-type",required = true )
	public String getEntityType() {
		return entityType;
	}
	
	@XmlAttribute(name="source-relation",required = true )
	public String getEntityRelation() {
		return entityRelation;
	}
	
	public Set<String> getFromRelationNames() {
			
		Set<String> relationNames = new HashSet<String>();
		
		// collect names of relations used in indexing fields 
		for (FieldIndexingConfig indexField : this.getIndexFields() ) {
			if ( indexField.getSourceFromRelation() != null )
				relationNames.add( indexField.getSourceFromRelation() );
		}
		
		return relationNames;
	}

	public Set<String> getToRelationNames() {
		
		Set<String> relationNames = new HashSet<String>();
		
		// collect names of relations used in indexing fields 
		for (FieldIndexingConfig indexField : this.getIndexFields() ) {
			if ( indexField.getSourceToRelation() != null )
				relationNames.add( indexField.getSourceToRelation() );
		}
		
		return relationNames;
	}
	
	private String name = null;
	
	@XmlAttribute(name="name", required = true)
	public String getName() {
		return name;
	}
	
}
