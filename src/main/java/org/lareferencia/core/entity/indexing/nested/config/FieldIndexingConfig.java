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

import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@XmlRootElement
@Setter
public class FieldIndexingConfig {
	
	public enum FIELD_TYPE { STRING, TEXT } 
	
	String name;
	FIELD_TYPE type = FIELD_TYPE.STRING;

	String sourceField;
	String sourceSubfield;
	String sourceRelation;
	String sourceMember;
	Boolean sortable = false;
	
	@XmlAttribute(name="name", required = true)
	public String getName() {
		return name;
	}
	
	@XmlAttribute(name="type")
	public FIELD_TYPE getType() {
		return type;
	}
	
	@XmlAttribute(name="source-field", required = true)
	public String getSourceField() {
		return sourceField;
	}
	
	@XmlAttribute(name="source-subfield", required = true)
	public String getSourceSubfield() {
		return sourceSubfield;
	}
	
	@XmlAttribute(name="source-relation")
	public String getSourceRelation() {
		return sourceRelation;
	}
	
	@XmlAttribute(name="source-member")
	public String getSourceMember() {
		return sourceMember;
	}

	@XmlAttribute(name="sortable", required = false)
	public Boolean getSortable() {
		return sortable;
	}

	String filter;
	@XmlAttribute(name="filter", required = false)
	public String getFilter() {
		return filter;
	}

	// This attribute is used to store any extra information
	Map<String,String> params = new HashMap<String,String>();
	@XmlAnyAttribute
	public Map<String,String> getParams() {
		return params;
	}


}
