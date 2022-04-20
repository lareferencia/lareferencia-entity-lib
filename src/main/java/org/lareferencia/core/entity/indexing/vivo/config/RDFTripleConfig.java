
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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@XmlRootElement(name="triple")
public class RDFTripleConfig {
	
	private TripleSubject subject = new TripleSubject();
	private TriplePredicate predicate = new TriplePredicate();
	private TripleObject object = new TripleObject();
	
	@XmlElement(name="subject")
	public TripleSubject getSubject() {
		return subject;
	}
	
	public void setSubject(TripleSubject subject) {
		this.subject = subject;
	}
	
	@XmlElement(name="predicate")
	public TriplePredicate getPredicate() {
		return predicate;
	}
	
	public void setPredicate(TriplePredicate predicate) {
		this.predicate = predicate;
	}
	
	@XmlElement(name="object")
	public TripleObject getObject() {
		return object;
	}
	
	public void setObject(TripleObject object) {
		this.object = object;
	}
	
	@XmlRootElement
	@Setter
	public static class TripleSubject {
		
		String type;
		String namespace;
		String prefix;
		String idType;
		String idSource;
		
		@XmlAttribute(name="type", required = true)
		public String getType() {
			return type;
		}
		
		@XmlAttribute(name="namespace", required = true)
		public String getNamespace() {
			return namespace;
		}
		
		@XmlAttribute(name="prefix", required = false)
		public String getPrefix() {
			return prefix;
		}
		
		@XmlAttribute(name="idType", required = true)
		public String getIdType() {
			return idType;
		}
		
		@XmlAttribute(name="idSource", required = false)
		public String getIdSource() {
			return idSource;
		}
	}
	
	@XmlRootElement
	@Setter
	public static class TriplePredicate {
		
		String type;
		String value;
		
		@XmlAttribute(name="type", required = true)
		public String getType() {
			return type;
		}
		
		@XmlAttribute(name="value", required = true)
		public String getValue() {
			return value;
		}
	}

	@XmlRootElement
	@Setter
	public static class TripleObject extends TripleSubject {
	
		String value;
		String storeAs;
		String parts;
		
		@XmlAttribute(name="value", required = false)
		public String getValue() {
			return value;
		}
		
		@XmlAttribute(name="storeAs", required = false)
		public String getStoreAs() {
			return storeAs;
		}
		
		@XmlAttribute(name="parts", required = false)
		public String getParts() {
			return parts;
		}
	}

}
