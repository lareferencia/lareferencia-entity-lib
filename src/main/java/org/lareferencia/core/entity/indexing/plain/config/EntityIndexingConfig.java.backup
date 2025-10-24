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

package org.lareferencia.core.entity.indexing.plain.config;

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
	
	private List<FieldIndexingConfig> indexFields = new LinkedList<FieldIndexingConfig>();
	private List<FieldIndexingConfig> indexRelatedIds = new LinkedList<FieldIndexingConfig>();

	
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

	@XmlElementWrapper(name="index-related-ids")
	@XmlElement(name="related-id")
	public List<FieldIndexingConfig> getIndexRelatedIds() {
		return indexRelatedIds;
	}

	@XmlAttribute(name="source-type",required = true )
	public String getEntityType() {
		return entityType;
	}
	
	
	public Set<String> getRelationNames() {
			
		Set<String> relationNames = new HashSet<String>();
		
		// collect names of relations used in indexing fields 
		for (FieldIndexingConfig indexField : this.getIndexFields() ) {
			if ( indexField.getSourceRelation() != null )
				relationNames.add( indexField.getSourceRelation() );
		}
		
		// collect names of relations used related ids
		for (FieldIndexingConfig indexField : this.getIndexRelatedIds() ) {
			if ( indexField.getSourceRelation() != null )
				relationNames.add( indexField.getSourceRelation() );
		}
		
		return relationNames;
		
	}
	
}
