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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@XmlRootElement(name="entity")
@Setter
public class XMLEntityInstance {
	
	
	private String type;
	private String ref;
	private List<String> semanticIdentifiers = new LinkedList<String>();
	
	private List<XMLFieldValueInstance> fields = new LinkedList<XMLFieldValueInstance>();

	public XMLEntityInstance(String type) {
		super();
		this.type = type;
	}
	
	public XMLEntityInstance(String type, String ref) {
		super();
		this.type = type;
		this.ref = ref;
	}

	public XMLEntityInstance() {
		super();
	}

	@XmlAttribute
	public String getType() {
		return type;
	}
	
	@XmlAttribute
	public String getRef() {
		return ref;
	}
	
	@XmlElement(name="semanticIdentifier")
	public List<String> getSemanticIdentifiers() {
		return semanticIdentifiers;
	}
	
	@XmlElement(name="field")
	public List<XMLFieldValueInstance> getFields() {
		return fields;
	}

	public void setFields(List<XMLFieldValueInstance> fields) {
		this.fields = new LinkedList<XMLFieldValueInstance>();
		for (XMLFieldValueInstance entityField : fields) {
			this.addField(entityField);
		}
	}

	public void addField(XMLFieldValueInstance field) {
		this.fields.add(field);
	}
}
