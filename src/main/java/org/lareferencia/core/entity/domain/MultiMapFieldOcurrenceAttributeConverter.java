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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Multimap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.persistence.AttributeConverter;
import java.io.IOException;


public class MultiMapFieldOcurrenceAttributeConverter implements AttributeConverter< Multimap<String, FieldOccurrence>, String > {

    private static Logger logger = LogManager.getLogger(MultiMapFieldOcurrenceAttributeConverter.class);

 
    static ObjectMapper objectMapper;

    public MultiMapFieldOcurrenceAttributeConverter() {
    	super();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());

        SimpleModule module = new SimpleModule();
        module.addDeserializer(FieldOccurrence.class, new FieldOccurrenceDeserializer());
        objectMapper.registerModule(module);    

        objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        objectMapper.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);
    }
    
    @Override
    public String convertToDatabaseColumn(Multimap<String, FieldOccurrence> data) {
 
        String dataJson = null;
        try {
            dataJson = objectMapper.writeValueAsString(data.asMap());
        } catch (final JsonProcessingException e) {
            logger.error("JSON writing error", e);
        }
 
        return dataJson;
    }
 
    @Override
    public Multimap<String, FieldOccurrence> convertToEntityAttribute(String dataJSON) {
 
    	if ( dataJSON == null )
    		dataJSON = "{}";

        Multimap<String, FieldOccurrence> multimap = null;

        try {
            JsonNode node = objectMapper.readTree(dataJSON);
            multimap = objectMapper.readValue(
                    objectMapper.treeAsTokens(node),
                    objectMapper.getTypeFactory().constructMapLikeType(
                            Multimap.class, String.class, FieldOccurrence.class));

        } catch (final IOException e) {
            logger.error("JSON reading error", e);
        }
 
        return multimap;
    }

    
 
}