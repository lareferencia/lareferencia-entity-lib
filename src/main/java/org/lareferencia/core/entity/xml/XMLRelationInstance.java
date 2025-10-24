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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Setter;


@XmlRootElement(name="relation")
@Setter
public class XMLRelationInstance  {
	
	private String fromEntityRef;
	private String toEntityRef;	
	
	@XmlAttribute(name="fromEntityRef")
	public String getFromEntityRef() {
		return fromEntityRef;
	}
	
	@XmlAttribute(name="toEntityRef")
	public String getToEntityRef() {
		return toEntityRef;
	}
	
	private List<XMLFieldValueInstance> fields = new LinkedList<XMLFieldValueInstance>();
	
	@XmlElementWrapper(name="attributes")
	@XmlElement(name="field")
	public List<XMLFieldValueInstance> getFields() {
		return fields;
	}

	public void setFields(List<XMLFieldValueInstance> fields) {
		this.fields = fields;
	}

	private String type;
	
	@XmlAttribute(name="type")
	public String getType() {
		return type;
	}

/*		
	private String lastUpdate;
	
	@XmlAttribute
	public String getLastUpdate() {
		return lastUpdate;
	}
*/	
	public XMLRelationInstance(String type) {
		super();
		this.type = type;
	}

	public XMLRelationInstance() {
		super();
	}

	
	
}
