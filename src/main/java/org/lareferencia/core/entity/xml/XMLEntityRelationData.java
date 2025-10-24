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

package org.lareferencia.core.entity.xml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.lareferencia.core.entity.domain.EntityRelationException;

import lombok.Setter;

@XmlRootElement(name="entity-relation-data")
@Setter
public class XMLEntityRelationData {
	
	private List<XMLEntityInstance> entities;
	private List<XMLRelationInstance> relations;

	public XMLEntityRelationData() {
		relations = new ArrayList<XMLRelationInstance>();
		entities = new ArrayList<XMLEntityInstance>();
	}

	@XmlElementWrapper(name="entities")
	@XmlElement(name="entity")
	public List<XMLEntityInstance> getEntities() {
		return entities;
	}

	public void setEntities(List<XMLEntityInstance> entities) {
		this.entities = entities;
	}

	public void addEntity(XMLEntityInstance entity) {
		entities.add(entity);
	}

	@XmlElementWrapper(name="relations")
	@XmlElement(name="relation")
	public List<XMLRelationInstance> getRelations() {
		return relations;
	}

	public void setRelations(List<XMLRelationInstance> relations) {
		this.relations = relations;
	}
	
	public void addRelationship(XMLRelationInstance relation) {
		relations.add(relation);
	}
	
	public boolean isConsistent() throws EntityRelationException  {
		
		boolean isConsistent = true;
		
		Set<String> entityReferences = new HashSet<String>();
		
		// collect references to entetities
		for (XMLEntityInstance entity:entities) {
			
			if ( entity.getType() == null )
				throw new EntityRelationException( String.format("Found Entity without type: ", entity.getRef()) );
			
			entityReferences.add( entity.getRef()) ;
		}
		
		Boolean isValidMember = false;
		
		for (XMLRelationInstance relation:relations) {
			
			
			isValidMember = entityReferences.contains( relation.getFromEntityRef() );
			
			if (!isValidMember)
				throw new EntityRelationException( String.format("Relation: %s From reference is not valid entity reference: %s", relation.getType(), relation.getFromEntityRef()) );
			
			isConsistent &= isValidMember ;
			
			isValidMember = entityReferences.contains( relation.getToEntityRef() );
			
			if (!isValidMember)
				throw new EntityRelationException( String.format("Relation: %s From reference is not valid entity reference: %s", relation.getType(), relation.getToEntityRef()) );

			isConsistent &= isValidMember ;			
				
		}
		
		return isConsistent;
	}
	
	
	private String lastUpdate;
	private String source;
	private String record;
	
	@XmlAttribute
	public String getLastUpdate() {
		return lastUpdate;
	}
		
	@XmlAttribute
	public String getSource() {
		return source;
	}

	@XmlAttribute
	public String getRecord() {
		return record;
	}
	
}
