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

import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@XmlRootElement
@Setter
public class FieldIndexingConfig {

	static final String DEFAULT_FIELD_TYPE = "keyword";

	String name;
	String type = DEFAULT_FIELD_TYPE;
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
	public String getType() {
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

	// Preferred values only is used to filter the values that are not preferred
	Boolean preferredValuesOnly = false;
	@XmlAttribute(name="preferred-values-only", required = false)
	public Boolean getPreferredValuesOnly() {
		return preferredValuesOnly;
	}

	String filter;
	@XmlAttribute(name="filter", required = false)
	public String getFilter() {
		return filter;
	}

	// This attribute is used to store any extra information
	Map<QName,String> qnameParams = new HashMap<QName,String>();
	@XmlAnyAttribute
	public Map<QName,String> getQnameParams() {
		return qnameParams;
	}

	// this translates the qnameParams to a Map<String,String>
	public Map<String,String> getParams() {
		Map<String,String> params = new HashMap<String,String>();

		for ( QName qname : qnameParams.keySet() ) {
			params.put(qname.getLocalPart(), qnameParams.get(qname));
		}

		return params;
	}


}
