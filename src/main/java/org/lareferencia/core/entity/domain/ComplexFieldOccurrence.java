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

import java.util.Hashtable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ComplexFieldOccurrence extends FieldOccurrence {
	
	final static ObjectMapper jsonMapper = new ObjectMapper();
	
	@Setter(AccessLevel.NONE)
	@EqualsAndHashCode.Include
	@JsonProperty(FIELDOCCURRENCE_JSON_VALUE_FIELD)
    Map<String, String> content = new Hashtable<String, String>();

	public ComplexFieldOccurrence(Map<String, String> content) {
		this.content = content;
	}
    	
	public ComplexFieldOccurrence addValue(String fieldname, String value) {
		this.content.put(fieldname, value);
		return this;
	}

    /**
     * params[0] = subfieldname
     */
	public String getValue(String ... params) throws EntityRelationException {
		
		// if no parameter provided will return serialized json as string 
		if ( params.length == 0) {
			
			try {
				return jsonMapper.writeValueAsString(content);
			} catch (JsonProcessingException e) {
				throw new EntityRelationException("Complex FieldOccurrence :: error occurring during data JSON serialization: " + e.getMessage() );
			}  
		
		} else {			
		
	    	String subfieldName = params[0];
	    
			return this.content.get(subfieldName);
		
		} 

	}
	
	@Override
	public Object getContent() {
		return content; 
	}

}
