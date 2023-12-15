package org.lareferencia.core.entity.domain;

import static org.mockito.ArgumentMatchers.nullable;

import java.io.IOException;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class FieldOccurrenceDeserializer extends StdDeserializer<FieldOccurrence> { 

        public FieldOccurrenceDeserializer() { 
            this(null); 
        } 

        public FieldOccurrenceDeserializer(Class<?> vc) { 
            super(vc); 
        }

        @Override
        public FieldOccurrence deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

            JsonNode node = jp.getCodec().readTree(jp);
            JsonNode dataNode = node.get(FieldOccurrence.FIELDOCCURRENCE_JSON_VALUE_FIELD);

            FieldOccurrence fo = null;

            if (dataNode == null)
                return null;
            
            if ( dataNode.isObject()) {

                // complex field
                ComplexFieldOccurrence cfo = new ComplexFieldOccurrence();

                Iterable<Entry<String, JsonNode>> fields = () -> dataNode.fields();

                for (Entry<String, JsonNode> entry : fields) {
                    cfo.addValue(entry.getKey(), entry.getValue().asText());
                }

                fo = cfo;

            } else {
                // simple field
                fo = new SimpleFieldOccurrence(dataNode.asText());

            }

            if ( node.get(FieldOccurrence.FIELDOCCURRENCE_JSON_LANG_FIELD) != null )
                fo.setLang(node.get(FieldOccurrence.FIELDOCCURRENCE_JSON_LANG_FIELD).asText());
            
            if ( node.get(FieldOccurrence.FIELDOCCURRENCE_JSON_PREFERRED_FIELD) != null )
                fo.setPreferred(node.get(FieldOccurrence.FIELDOCCURRENCE_JSON_PREFERRED_FIELD).asBoolean());

            return fo;

        }
    }