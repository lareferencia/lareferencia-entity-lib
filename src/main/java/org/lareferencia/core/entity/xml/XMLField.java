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
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@XmlRootElement(name="field")
@Setter
public class XMLField {
	
	private String name;
	private String description;
	private Boolean multilingual = false;
	
	private List<XMLField> fields = new LinkedList<XMLField>();
	private Integer maxOccurs = 1;
	
	public XMLField(String name) {
		super();
		this.name = name;
	}
	
	public XMLField(String name, Integer maxOccurs) {
		super();
		this.name = name;
		this.maxOccurs = maxOccurs;
	}
	
	public XMLField() {
		super();
	}


	@XmlAttribute
	public String getName() {
		return name;
	}
	
	
	@XmlAttribute
	public String getDescription() {
		return description;
	}
	
	@XmlAttribute
	public Integer getMaxOccurs() {
		return maxOccurs;
	}
	
	@XmlAttribute
	public Boolean getMultilingual() {
		return multilingual;
	}
	
	@XmlElement(name="field")
	public List<XMLField> getFields() {
		return fields;
	}

	public void setFields(List<XMLField> fields) {
		this.fields = new LinkedList<XMLField>();
		for (XMLField entityField : fields) {
			this.addField(entityField);
		}
	}

	public void addField(XMLField field) {
		this.fields.add(field);
	}

	

}

