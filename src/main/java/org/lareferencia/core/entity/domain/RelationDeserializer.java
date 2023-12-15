package org.lareferencia.core.entity.domain;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class RelationDeserializer extends StdDeserializer<Relation> { 

        public RelationDeserializer() { 
            this(null); 
        } 

        public RelationDeserializer(Class<?> vc) { 
            super(vc); 
        }

        @Override
        public Relation deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

            // SET DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY to true
            // to allow single values to be deserialized as arrays
            JsonNode node = jp.getCodec().readTree(jp);

            JsonNode targetNode = node.get(Relation.RELATION_JSON_TARGET_FIELD);
            JsonNode fieldsNode = node.get(FieldOccurrenceContainer.FIELDOCCURRENCE_JSON_OCURRENCES_FIELD);

            if (targetNode == null) {
                throw new IOException("Relation target field is mandatory");
            }

            // create relation 
            Relation fo = new Relation( UUID.fromString( targetNode.asText() ));

            // if fields are present, add them to the relation
            if ( fieldsNode != null ) {

                fieldsNode.fieldNames().forEachRemaining( fieldName -> {
                    try {

                            if (fieldsNode.get(fieldName).isArray()) {
                                // if the field is an array, add each occurrence
                                for (JsonNode fieldNode : fieldsNode.get(fieldName)) {
                                    fo.addFieldOccurrence(fieldName, ctxt.readValue(fieldNode.traverse(jp.getCodec()), FieldOccurrence.class));
                                }
                            } else {
                                // if the field is not an array, add the single occurrence
                                fo.addFieldOccurrence(fieldName, ctxt.readValue(fieldsNode.get(fieldName).traverse(jp.getCodec()), FieldOccurrence.class));
                            }                    
                        } catch (IOException e) {
                    }
                });    
                    
            }

            return fo;
        }
    }