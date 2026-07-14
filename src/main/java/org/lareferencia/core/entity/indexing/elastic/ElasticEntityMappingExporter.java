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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.lareferencia.core.entity.indexing.nested.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.IndexingConfiguration;

public class ElasticEntityMappingExporter {

    private static final String NULL_VALUE = "null";

    private final ObjectMapper objectMapper;
    private final ElasticEntityMappingBuilder mappingBuilder;

    public ElasticEntityMappingExporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mappingBuilder = new ElasticEntityMappingBuilder();
    }

    public String exportJson(String configFileFullPath, String entityTypeName) throws Exception {
        IndexingConfiguration config = IndexingConfiguration.loadFromXml(configFileFullPath);
        return exportJson(config, entityTypeName);
    }

    public String exportJson(IndexingConfiguration config, String entityTypeName) throws Exception {
        List<EntityIndexingConfig> selectedEntityConfigs = new ArrayList<EntityIndexingConfig>();

        for (EntityIndexingConfig entityConfig : config.getEntityIndices()) {
            if (!matchesEntityType(entityConfig, entityTypeName)) {
                continue;
            }

            selectedEntityConfigs.add(entityConfig);
        }

        if (selectedEntityConfigs.isEmpty()) {
            throw new IllegalArgumentException("No indexed entity found for entityTypeName: " + entityTypeName);
        }

        if (selectedEntityConfigs.size() == 1) {
            return objectMapper.writeValueAsString(mappingBuilder.createIndexDefinition(selectedEntityConfigs.get(0)));
        }

        Map<String, Object> indices = new LinkedHashMap<String, Object>();
        for (EntityIndexingConfig entityConfig : selectedEntityConfigs) {
            indices.put(entityConfig.getName(), mappingBuilder.createIndexDefinition(entityConfig));
        }

        return objectMapper.writeValueAsString(indices);
    }

    public Path exportJsonToFile(String configFileFullPath, String entityTypeName, String outputFileFullPath)
            throws Exception {
        Path outputPath = resolveOutputPath(configFileFullPath, outputFileFullPath);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = exportJson(configFileFullPath, entityTypeName);
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);
        return outputPath;
    }

    private boolean matchesEntityType(EntityIndexingConfig entityConfig, String entityTypeName) {
        return isNullOrBlank(entityTypeName) || Objects.equals(entityConfig.getEntityType(), entityTypeName.trim());
    }

    private boolean isNullOrBlank(String value) {
        return value == null || value.trim().isEmpty() || NULL_VALUE.equals(value.trim());
    }

    private Path resolveOutputPath(String configFileFullPath, String outputFileFullPath) {
        if (isNullOrBlank(outputFileFullPath)) {
            return Path.of(configFileFullPath + ".mapping.json");
        }

        return Path.of(outputFileFullPath);
    }
}
