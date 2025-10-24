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

package org.lareferencia.core.entity.indexing.elastic;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class JSONEntityElasticSerializer extends StdSerializer<JSONEntityElastic> {

	
	protected JSONEntityElasticSerializer() {
		this(null);
	}
	
	protected JSONEntityElasticSerializer(Class<JSONEntityElastic> t) {
		super(t);
	}

	@Override
	public void serialize(JSONEntityElastic entity, JsonGenerator gen, SerializerProvider provider) throws IOException {
		
		gen.writeStartObject();
		
		// Write String fields
		gen.writeStringField("id", entity.getId());

		if ( entity.getSemanticIds() != null && entity.getSemanticIds().size() > 0)
			gen.writeObjectField("semanticIdentifiers", entity.getSemanticIds());

		if ( entity.getProvenanceIds() != null && entity.getProvenanceIds().size() > 0)
			gen.writeObjectField("provenances", entity.getProvenanceIds());

		if ( entity.getType() != null && entity.getType().length() > 0)
			gen.writeStringField("entityTypeName", entity.getType());
		
		for ( Entry<String, Collection<String>> entry:  entity.getFieldOccurrenceMap().entrySet() ) {
			gen.writeObjectField(entry.getKey() , entry.getValue());
		}
		
		for ( Entry<String, Collection<JSONEntityElastic>> entry:  entity.getRelatedEntitiesMap().entrySet() ) {
			gen.writeObjectField(entry.getKey() , entry.getValue());
		}
        gen.writeEndObject();
		
	}

}
