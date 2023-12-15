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

package org.lareferencia.core.entity.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SimpleFieldOccurrence extends FieldOccurrence {

	/**
	 * Single string value
	 **/
	@Setter(AccessLevel.NONE)
	@EqualsAndHashCode.Include
	@JsonProperty(FIELDOCCURRENCE_JSON_VALUE_FIELD)
	String content;
	
	public SimpleFieldOccurrence(String value) {
		this.content = value;
	}

	 /**
     * no params
     */
	public String getValue(String ... params) throws EntityRelationException {
		
		if ( params.length > 0)
			throw new EntityRelationException("SimpleFieldOccurrence :: addValue with fieldname was called!!: " + params );
		
		return content;
	}

	@Override
	public Object getContent() {
		return content; 
	}
	
}
