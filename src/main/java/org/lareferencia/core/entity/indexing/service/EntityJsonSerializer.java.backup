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

package org.lareferencia.core.entity.indexing.service;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class EntityJsonSerializer extends StdSerializer<IEntity> {

	protected EntityJsonSerializer() {
		this(null);
	}
	
	protected EntityJsonSerializer(Class<IEntity> t) {
		super(t);
	}

	@Override
	public void serialize(IEntity entity, JsonGenerator gen, SerializerProvider provider) throws IOException {
		
		gen.writeStartObject();
		
		// Write String fields
		gen.writeStringField("id", entity.getId());
		gen.writeObjectField("semanticIdentifiers", entity.getSemanticIds());
		gen.writeObjectField("provenances", entity.getProvenanceIds());
		gen.writeStringField("entityTypeName", entity.getType());
		
		gen.writeObjectField("fields", entity.getFieldOccurrenceMap()); 
              
		gen.writeObjectField("_links", entity.getRelatedLinksMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new Link(e.getValue()))));
        
        gen.writeEndObject();
		
	}
	
	@AllArgsConstructor
	@Getter
	@JsonSerialize
	class Link {
		
		public String href;
		
	}

}
