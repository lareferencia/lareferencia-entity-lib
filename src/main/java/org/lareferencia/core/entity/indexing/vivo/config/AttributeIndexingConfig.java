
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import lombok.Setter;

@XmlRootElement(name="source-attribute")
@Setter
public class AttributeIndexingConfig {
	
	String name;
	String filter;
	Map<QName,String> qnameParams = new HashMap<QName,String>();
	Boolean preferredValuesOnly = false;
	
	@XmlAttribute(name="name", required = true)
	public String getName() {
		return name;
	}
	
	private List<RDFTripleConfig> targetTriples = new LinkedList<RDFTripleConfig>();;
	private List<AttributeIndexingConfig> subAttributes = new LinkedList<AttributeIndexingConfig>();

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
	
	@XmlElementWrapper(name="sub-attributes")
	@XmlElement(name="source-attribute")
	public List<AttributeIndexingConfig> getSubAttributes() {
		return subAttributes;
	}
	
	public void setSubAttributes(List<AttributeIndexingConfig> sourceAttributes) {
		this.subAttributes = new LinkedList<AttributeIndexingConfig>();
		for (AttributeIndexingConfig attribute : sourceAttributes) {
			this.subAttributes.add(attribute);
		}
	}
	
	@XmlAttribute(name="filter", required = false)
	public String getFilter() {
		return filter;
	}
	
	// Get any extra information as params
	@XmlAnyAttribute
	public Map<QName,String> getQnameParams() {
		return qnameParams;
	}

	// Translates the qnameParams to a Map<String,String>
	public Map<String,String> getParams() {
		Map<String,String> params = new HashMap<String,String>();

		for ( QName qname : qnameParams.keySet() ) {
			params.put(qname.getLocalPart(), qnameParams.get(qname));
		}

		return params;
	}
	
	// Preferred values only is used to filter the values that are not preferred		
	@XmlAttribute(name="preferred-values-only", required = false)
	public Boolean getPreferredValuesOnly() {
		return preferredValuesOnly;
	}

}
