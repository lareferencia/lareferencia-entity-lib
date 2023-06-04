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

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import org.lareferencia.core.entity.services.IFieldValueInstance;

import javax.xml.bind.annotation.XmlAccessType;


import lombok.Setter;

@XmlRootElement(name="field")
@Setter
public class XMLFieldValueInstance implements IFieldValueInstance {
	
	private String name;
	private String lang;
	private String value;
	private Boolean preferred;
	
	private List<XMLFieldValueInstance> fields = new LinkedList<XMLFieldValueInstance>();
	
	public XMLFieldValueInstance(String name, String value) {
		super();
		this.name = name;
		this.value = value;
		this.preferred = false;
	}
	
	public XMLFieldValueInstance(String name, String lang, String value) {
		super();
		this.name = name;
		this.lang = lang;
		this.value = value;
		this.preferred = false;
	}

	public XMLFieldValueInstance(String name, String lang, String value, Boolean preferred) {
		super();
		this.name = name;
		this.lang = lang;
		this.value = value;
		this.preferred = preferred;
	}
	
	public XMLFieldValueInstance() {
		super();
	}

	@XmlAttribute
	public String getName() {
		return name;
	}
	
		
	@XmlAttribute
	public String getLang() {
		return lang;
	}

	@XmlAttribute
	public String getValue() {
		return value;
	}

	@XmlAttribute
	public Boolean getPreferred() {
		return preferred;
	}
	
	@XmlElement(name="field")
	public List<XMLFieldValueInstance> getFields() {
		return fields;
	}


}

