
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

package org.lareferencia.core.entity.indexing.vivo.config;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@XmlRootElement(name="source-relation")
@Setter
public class RelationIndexingConfig {

	String name;
	
	@XmlAttribute(name="name", required = true)
	public String getName() {
		return name;
	}
	
	private List<RDFTripleConfig> targetTriples = new LinkedList<RDFTripleConfig>();

	@XmlElementWrapper(name="target-triples")
	@XmlElement(name="triple")
	public List<RDFTripleConfig> getTargetTriples() {
		return targetTriples;
	}
	
	public void setTargetTriples(List<RDFTripleConfig> targetTriples) {
		this.targetTriples = new LinkedList<RDFTripleConfig>();
		for (RDFTripleConfig triple : targetTriples) {
			this.targetTriples.add(triple);
		}
	}
	
	private List<AttributeIndexingConfig> sourceAttributes = new LinkedList<AttributeIndexingConfig>();

	@XmlElementWrapper(name="source-relation-attributes")
	@XmlElement(name="source-attribute")
	public List<AttributeIndexingConfig> getSourceAttributes() {
		return sourceAttributes;
	}
	
	public void setSourceAttributes(List<AttributeIndexingConfig> sourceAttributes) {
		this.sourceAttributes = new LinkedList<AttributeIndexingConfig>();
		for (AttributeIndexingConfig attribute : sourceAttributes) {
			this.sourceAttributes.add(attribute);
		}
	}
}
