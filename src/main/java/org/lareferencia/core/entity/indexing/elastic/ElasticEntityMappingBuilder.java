/*
 *   Copyright (c) 2013-2026. LA Referencia / Red CLARA and others
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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lareferencia.core.entity.indexing.nested.config.AnalysisConfig;
import org.lareferencia.core.entity.indexing.nested.config.AnalyzerConfig;
import org.lareferencia.core.entity.indexing.nested.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.IndexSettingsConfig;

public class ElasticEntityMappingBuilder {

    public static final String ELASTIC_PARAM_PREFIX = "elastic-param-";
    private static final String MAPPING_PROPERTIES_STR = "properties";
    private static final String ID_FIELD = "id";
    private static final String ID_FIELD_TYPE = "keyword";

    public Map<String, Object> createIndexDefinition(EntityIndexingConfig entityConfig) {
        Map<String, Object> index = new LinkedHashMap<String, Object>();
        Map<String, Object> settings = createSettings(entityConfig);
        if (!settings.isEmpty()) {
            index.put("settings", settings);
        }
        index.put("mappings", createMapping(entityConfig));
        return index;
    }

    public Map<String, Object> createMapping(EntityIndexingConfig entityConfig) {
        Map<String, Object> mapping = new LinkedHashMap<String, Object>();
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        mapping.put(MAPPING_PROPERTIES_STR, properties);

        addFields(properties, entityConfig.getIndexFields());
        properties.put(ID_FIELD, createTypeMapping(ID_FIELD_TYPE));
        addNestedEntities(properties, entityConfig.getIndexNestedEntities());

        return mapping;
    }

    public Map<String, Object> createSettings(EntityIndexingConfig entityConfig) {
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        IndexSettingsConfig indexSettings = entityConfig.getIndexSettings();
        if (indexSettings == null || !indexSettings.hasSettings()) {
            return settings;
        }

        Map<String, Object> index = new LinkedHashMap<String, Object>();
        if (indexSettings.getNumberOfShards() != null) {
            index.put("number_of_shards", indexSettings.getNumberOfShards());
        }
        if (indexSettings.getNumberOfReplicas() != null) {
            index.put("number_of_replicas", indexSettings.getNumberOfReplicas());
        }
        if (!index.isEmpty()) {
            settings.put("index", index);
        }

        Map<String, Object> analysis = createAnalysis(indexSettings.getAnalysis());
        if (!analysis.isEmpty()) {
            settings.put("analysis", analysis);
        }

        return settings;
    }

    private Map<String, Object> createAnalysis(AnalysisConfig analysisConfig) {
        Map<String, Object> analysis = new LinkedHashMap<String, Object>();
        if (analysisConfig == null || !analysisConfig.hasAnalyzers()) {
            return analysis;
        }

        Map<String, Object> analyzers = new LinkedHashMap<String, Object>();
        for (AnalyzerConfig analyzerConfig : analysisConfig.getAnalyzers()) {
            analyzers.put(analyzerConfig.getName(), createAnalyzer(analyzerConfig));
        }
        analysis.put("analyzer", analyzers);

        return analysis;
    }

    private Map<String, Object> createAnalyzer(AnalyzerConfig analyzerConfig) {
        Map<String, Object> analyzer = new LinkedHashMap<String, Object>();

        addIfPresent(analyzer, "type", analyzerConfig.getType());
        addIfPresent(analyzer, "tokenizer", analyzerConfig.getTokenizer());

        if (analyzerConfig.getFilters() != null && !analyzerConfig.getFilters().isEmpty()) {
            analyzer.put("filter", analyzerConfig.getFilters());
        }
        if (analyzerConfig.getCharFilters() != null && !analyzerConfig.getCharFilters().isEmpty()) {
            analyzer.put("char_filter", analyzerConfig.getCharFilters());
        }

        analyzerConfig.getParams().forEach((key, value) -> analyzer.put(key, parseParamValue(value)));
        return analyzer;
    }

    private void addFields(Map<String, Object> properties, Collection<FieldIndexingConfig> fieldConfigs) {
        for (FieldIndexingConfig fieldConfig : fieldConfigs) {
            Map<String, Object> fieldMapping = createTypeMapping(fieldConfig.getType());
            addElasticParams(fieldMapping, fieldConfig.getParams());
            properties.put(fieldConfig.getName(), fieldMapping);
        }
    }

    private Map<String, Object> createTypeMapping(String type) {
        Map<String, Object> fieldMapping = new LinkedHashMap<String, Object>();
        fieldMapping.put("type", type);
        return fieldMapping;
    }

    private void addNestedEntities(Map<String, Object> properties, Collection<EntityIndexingConfig> nestedEntityConfigs) {
        for (EntityIndexingConfig nestedEntityConfig : nestedEntityConfigs) {
            Map<String, Object> nestedMapping = new LinkedHashMap<String, Object>();
            Map<String, Object> nestedProperties = new LinkedHashMap<String, Object>();
            nestedMapping.put(MAPPING_PROPERTIES_STR, nestedProperties);
            properties.put(nestedEntityConfig.getName(), nestedMapping);

            addFields(nestedProperties, nestedEntityConfig.getIndexFields());
            nestedProperties.put(ID_FIELD, createTypeMapping(ID_FIELD_TYPE));
        }
    }

    private void addElasticParams(Map<String, Object> fieldMapping, Map<String, String> params) {
        params.forEach((key, value) -> {
            if (key.startsWith(ELASTIC_PARAM_PREFIX)) {
                fieldMapping.put(key.replace(ELASTIC_PARAM_PREFIX, ""), parseParamValue(value));
            }
        });
    }

    private Object parseParamValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        if ("true".equalsIgnoreCase(trimmedValue) || "false".equalsIgnoreCase(trimmedValue)) {
            return Boolean.valueOf(trimmedValue);
        }

        try {
            return Integer.valueOf(trimmedValue);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private void addIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value);
        }
    }
}
