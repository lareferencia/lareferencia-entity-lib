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
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="entity-relation-metamodel")
public class XMLEntityRelationMetamodel {

	private List<XMLEntityType> entities;
	private List<XMLRelationType> relations;

	public XMLEntityRelationMetamodel() {
		relations = new ArrayList<XMLRelationType>();
		entities = new ArrayList<XMLEntityType>();
	}

	@XmlElementWrapper(name="entities")
	@XmlElement(name="entity")
	public List<XMLEntityType> getEntities() {
		return entities;
	}

	public void setEntities(List<XMLEntityType> entities) {
		this.entities = entities;
	}

	public void addEntity(XMLEntityType entity) {
		entities.add(entity);
	}

	@XmlElementWrapper(name="relations")
	@XmlElement(name="relation")
	public List<XMLRelationType> getRelations() {
		return relations;
	}

	public void setRelations(List<XMLRelationType> relations) {
		this.relations = relations;
	}
	
	public void addRelationship(XMLRelationType relation) {
		relations.add(relation);
	}
	
}
