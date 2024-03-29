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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;


@XmlRootElement(name="member")
@Setter
public class XMLRelationMember {
	
	private String label;
	private String entityName;
	private Integer maxOccurs = 1;
	
	public XMLRelationMember(String label, String entity) {
		super();
		this.label = label;
	}
	
	public XMLRelationMember() {
		super();
		// TODO Auto-generated constructor stub
	}

	@XmlAttribute
	public String getLabel() {
		return label;
	}
	
	@XmlAttribute(name="entity")
	public String getEntityName() {
		return entityName;
	}
	
	@XmlAttribute
	public Integer getMaxOccurs() {
		return maxOccurs;
	}
}

