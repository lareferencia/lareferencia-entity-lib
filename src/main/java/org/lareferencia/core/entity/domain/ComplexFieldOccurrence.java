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

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;

import org.lareferencia.core.util.MapAttributeConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@jakarta.persistence.Entity
@DiscriminatorValue("C")
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ComplexFieldOccurrence extends FieldOccurrence {
	
	
	final static ObjectMapper jsonMapper = new ObjectMapper();
	

    public ComplexFieldOccurrence(FieldType field) {
		super(field);
	}

	/**
	 * { subfield_name: value }
	 * Table<subfield, value>
	 **/
	@Setter(AccessLevel.NONE)
	@Column(name="content", columnDefinition="TEXT")
	@Convert(converter = MapAttributeConverter.class)
	@EqualsAndHashCode.Include
    Map<String, String> content = new Hashtable<String, String>();
    
	
	 /**
     * params[0] = subfieldname
     * params[1] = value
     */
    public FieldOccurrence addValue(String ... params) throws EntityRelationException {
    	
		if ( params.length != 2)
			throw new EntityRelationException("Complex FieldOccurrence :: addValue incorret parameters !! " + params );
    	
    	String subfieldName = params[0];
    	String value = params[1];
    	
		// if not subfield name supplied $ is used (means root content of the field)
		if ( !fieldType.isSubfield(subfieldName) ) 
				throw new EntityRelationException("FieldOccurrence :: subfield " + subfieldName + " doesn´t exist in " + fieldType.getName() );

		this.content.put(subfieldName, value);
		
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
	    
			// if not subfield name supplied $ is used (means root content of the field)
			if ( subfieldName == null || !fieldType.isSubfield(subfieldName) ) 
					throw new EntityRelationException("FieldOccurrence :: subfield " + subfieldName + " doesn´t exist in " + fieldType.getName() );
	
			return this.content.get(subfieldName);
		
		} 

	}
	

	@Override
	public Object getContent() {
		return content; 
	}



}
