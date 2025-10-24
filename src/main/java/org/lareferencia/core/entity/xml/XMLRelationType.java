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

import java.util.LinkedList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlAttribute;

import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@Setter
@XmlRootElement(name="relation")
public class XMLRelationType  {
	
	private List<XMLField> fields = new LinkedList<XMLField>();

	
	private String name;
	private String description;
	
	private String fromEntityName;
	private String toEntityName;	
	
	@XmlAttribute(name="fromEntity")
	public String getFromEntityName() {
		return fromEntityName;
	}
	
	@XmlAttribute(name="toEntity")
	public String getToEntityName() {
		return toEntityName;
	}

	public XMLRelationType(String name) {
		super();
		this.name = name;
	}

	public XMLRelationType() {
		super();
	}

	@XmlElementWrapper(name="attributes")
	@XmlElement(name="field")
	public List<XMLField> getFields() {
		return fields;
	}

	public void setFields(List<XMLField> fields) {
		this.fields = fields;
	}

	@XmlAttribute(name="name")
	public String getName() {
		return name;
	}

	@XmlAttribute
	public String getDescription() {
		return description;
	}	
	
}
