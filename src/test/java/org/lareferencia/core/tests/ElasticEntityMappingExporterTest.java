package org.lareferencia.core.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lareferencia.core.entity.indexing.elastic.ElasticEntityMappingExporter;

public class ElasticEntityMappingExporterTest {

    @TempDir
    Path tempDir;

    @Test
    public void exportsJsonMappingFromNestedEntityIndexingConfig() throws Exception {
        Path configFile = tempDir.resolve("entity-indexing-config.xml");
        Files.writeString(configFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <entity-indexing-config>
                    <indexed-entity name="publication" source-type="Publication">
                        <index-settings number-of-shards="1" number-of-replicas="1">
                            <analysis>
                                <analyzer name="accent_insensitive" tokenizer="standard">
                                    <filter>lowercase</filter>
                                    <filter>asciifolding</filter>
                                </analyzer>
                            </analysis>
                        </index-settings>
                        <index-fields>
                            <index-field name="title" source-field="title" type="text" elastic-param-analyzer="accent_insensitive"/>
                            <index-field name="title_completion" source-field="title" type="completion" elastic-param-analyzer="simple" elastic-param-max_input_length="50" elastic-param-preserve_position_increments="true"/>
                            <index-field name="doi" source-field="identifier.doi"/>
                        </index-fields>
                        <index-nested-entities>
                            <indexed-entity name="author" source-type="Person" source-relation="Authorship">
                                <index-fields>
                                    <index-field name="name" source-field="name" type="keyword"/>
                                </index-fields>
                            </indexed-entity>
                        </index-nested-entities>
                    </indexed-entity>
                </entity-indexing-config>
                """);

        String json = new ElasticEntityMappingExporter().exportJson(configFile.toString(), null);

        assertTrue(json.contains("\"settings\""));
        assertTrue(json.contains("\"analysis\""));
        assertTrue(json.contains("\"analyzer\""));
        assertTrue(json.contains("\"accent_insensitive\""));
        assertTrue(json.contains("\"tokenizer\" : \"standard\""));
        assertTrue(json.contains("\"lowercase\""));
        assertTrue(json.contains("\"asciifolding\""));
        assertTrue(json.contains("\"number_of_shards\" : 1"));
        assertTrue(json.contains("\"number_of_replicas\" : 1"));
        assertTrue(json.contains("\"mappings\""));
        assertTrue(json.contains("\"properties\""));
        assertTrue(json.contains("\"title\""));
        assertTrue(json.contains("\"type\" : \"text\""));
        assertTrue(json.contains("\"analyzer\" : \"accent_insensitive\""));
        assertTrue(json.contains("\"title_completion\""));
        assertTrue(json.contains("\"max_input_length\" : 50"));
        assertTrue(json.contains("\"preserve_position_increments\" : true"));
        assertTrue(json.contains("\"doi\""));
        assertTrue(json.contains("\"type\" : \"keyword\""));
        assertTrue(json.contains("\"author\""));
        assertTrue(json.contains("\"id\""));
    }

}
