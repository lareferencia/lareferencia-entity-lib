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

package org.lareferencia.core.entity.indexing.solr;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Id;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.lareferencia.core.entity.indexing.service.EntityFieldValue;
import org.lareferencia.core.entity.indexing.service.IEntity;
import org.lareferencia.core.entity.indexing.service.EntityFieldValue.LangFieldType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EntitySolr implements IEntity {

    public static final String COLLECTION = "entity";

    public static final String ID_FIELD = "id";
    public static final String TYPE_FIELD = "type";
    
    public static final String DYNAMIC_FIELD_PREFIX = "fs.";
    public static final String DYNAMIC_TXT_FIELD_PREFIX = "ft.";
    public static final String DYNAMIC_SORT_FIELD_PREFIX = "sf.";
    public static final String DYNAMIC_RELID_PREFIX = "is.";
    public static final String DYNAMIC_LINK_PREFIX = "ls.";
    public static final String DYNAMIC_DATE_PREFIX = "df.";
    public static final String DYNAMIC_DATERANGE_PREFIX = "dr.";

    public static final String DYNAMIC_POR_FIELD_PREFIX = "por.";
    public static final String DYNAMIC_ENG_FIELD_PREFIX = "eng.";
    public static final String DYNAMIC_SPA_FIELD_PREFIX = "spa.";
    public static final String DYNAMIC_FRA_FIELD_PREFIX = "fra.";
        
    /**
     * Undefined language prefix
     */
    public static final String DYNAMIC_UND_FIELD_PREFIX = "und.";

    public static final String TYPE_FIELD_NAME = "type";

    public EntitySolr(String id, String type) {
        super();
        this.id = id;
        this.type = type;
    }

    @Id
    protected String id;

    protected List<String> semanticIds = new ArrayList<>();

    protected List<String> provenanceIds = new ArrayList<>();

    protected String type;

    protected Map<String, List<String>> datesByFieldName = new HashMap<>();
    protected Map<String, List<String>> datesRangeByFieldName = new HashMap<>();
    protected Map<String, List<String>> valuesByFieldName = new HashMap<>();
    protected Map<String, List<EntityFieldValue>> entityValuesByFieldName = new HashMap<>();
    protected Map<String, List<String>> porValuesByFieldName = new HashMap<>();
    protected Map<String, List<String>> engValuesByFieldName = new HashMap<>();
    protected Map<String, List<String>> spaValuesByFieldName = new HashMap<>();
    protected Map<String, List<String>> fraValuesByFieldName = new HashMap<>();
    protected Map<String, List<String>> undValuesByFieldName = new HashMap<>();
    protected Map<String, String> sortValueByFieldName = new HashMap<>();
    protected Map<String, List<String>> identifiersByRelation = new HashMap<>();
    protected Map<String, String> linksByRelation = new HashMap<>();

    public SolrInputDocument toSolrInputDocument() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField(ID_FIELD, id);
        doc.addField(TYPE_FIELD_NAME, type);
        doc.addField("semantic_id", semanticIds);
        doc.addField("provenance_id", provenanceIds);

        datesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_DATE_PREFIX + key, value));
        datesRangeByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_DATERANGE_PREFIX + key, value));
        valuesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_FIELD_PREFIX + key, value));
        porValuesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_POR_FIELD_PREFIX + key, value));
        engValuesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_ENG_FIELD_PREFIX + key, value));
        spaValuesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_SPA_FIELD_PREFIX + key, value));
        fraValuesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_FRA_FIELD_PREFIX + key, value));
        undValuesByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_UND_FIELD_PREFIX + key, value));
        sortValueByFieldName.forEach((key, value) -> doc.addField(DYNAMIC_SORT_FIELD_PREFIX + key, value));
        identifiersByRelation.forEach((key, value) -> doc.addField(DYNAMIC_RELID_PREFIX + key, value));
        linksByRelation.forEach((key, value) -> doc.addField(DYNAMIC_LINK_PREFIX + key, value));

        return doc;
    }

    public static EntitySolr fromSolrDocument(SolrDocument doc) {
        EntitySolr entity = new EntitySolr();
        entity.setId(doc.getFieldValue(ID_FIELD) != null ? doc.getFieldValue(ID_FIELD).toString() : null);
        entity.setType(doc.getFieldValue(TYPE_FIELD_NAME) != null ? doc.getFieldValue(TYPE_FIELD_NAME).toString() : null);
        entity.setSemanticIds(doc.getFieldValues("semantic_id").stream().map(Object::toString).collect(Collectors.toList()));
        entity.setProvenanceIds(doc.getFieldValues("provenance_id").stream().map(Object::toString).collect(Collectors.toList()));

        doc.getFieldNames().forEach(fieldName -> {
            if (fieldName.startsWith(DYNAMIC_DATE_PREFIX)) {
                entity.datesByFieldName.put(fieldName.substring(DYNAMIC_DATE_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_DATERANGE_PREFIX)) {
                entity.datesRangeByFieldName.put(fieldName.substring(DYNAMIC_DATERANGE_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_FIELD_PREFIX)) {
                entity.valuesByFieldName.put(fieldName.substring(DYNAMIC_FIELD_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_POR_FIELD_PREFIX)) {
                entity.porValuesByFieldName.put(fieldName.substring(DYNAMIC_POR_FIELD_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_ENG_FIELD_PREFIX)) {
                entity.engValuesByFieldName.put(fieldName.substring(DYNAMIC_ENG_FIELD_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_SPA_FIELD_PREFIX)) {
                entity.spaValuesByFieldName.put(fieldName.substring(DYNAMIC_SPA_FIELD_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_FRA_FIELD_PREFIX)) {
                entity.fraValuesByFieldName.put(fieldName.substring(DYNAMIC_FRA_FIELD_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_UND_FIELD_PREFIX)) {
                entity.undValuesByFieldName.put(fieldName.substring(DYNAMIC_UND_FIELD_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_SORT_FIELD_PREFIX)) {
                entity.sortValueByFieldName.put(fieldName.substring(DYNAMIC_SORT_FIELD_PREFIX.length()), (String) doc.getFieldValue(fieldName));
            } else if (fieldName.startsWith(DYNAMIC_RELID_PREFIX)) {
                entity.identifiersByRelation.put(fieldName.substring(DYNAMIC_RELID_PREFIX.length()), doc.getFieldValues(fieldName).stream().map(Object::toString).collect(Collectors.toList()));
            } else if (fieldName.startsWith(DYNAMIC_LINK_PREFIX)) {
                entity.linksByRelation.put(fieldName.substring(DYNAMIC_LINK_PREFIX.length()), (String) doc.getFieldValue(fieldName));
            }
        });

        return entity;
    }

    public void setValuesByFieldName(Map<String, List<String>> values) {
        valuesByFieldName = values;
    }

    public Map<String, List<String>> getValuesByFieldName() {
        Map<String, List<String>> values = new HashMap<>();
        entityValuesByFieldName.forEach((fieldName, listEntityValues) -> {
            List<String> list = new LinkedList<>();
            for (EntityFieldValue entityValue : listEntityValues) {
                list.add(entityValue.getValue());
            }
            values.put(fieldName, list);
        });
        return values;
    }
    
    public Map<String, List<String>> getUndValuesByFieldName() {
        return getEntityValuesByLang(LangFieldType.UND);
    }    

    public Map<String, List<String>> getPorValuesByFieldName() {
        return getEntityValuesByLang(LangFieldType.POR);
    }
    
    public Map<String, List<String>> getSpaValuesByFieldName() {
        return getEntityValuesByLang(LangFieldType.SPA);
    }
    
    public Map<String, List<String>> getFraValuesByFieldName() {
        return getEntityValuesByLang(LangFieldType.FRA);
    }
    
    public Map<String, List<String>> getEngValuesByFieldName() {
        return getEntityValuesByLang(LangFieldType.ENG);
    }    

    public void setUndValuesByFieldName(Map<String, List<String>> values) {
        setEntityValuesWithLang(values, LangFieldType.UND);
    }

    public void setPorValuesByFieldName(Map<String, List<String>> values) {
        setEntityValuesWithLang(values, LangFieldType.POR);
    }
    
    public void setEngValuesByFieldName(Map<String, List<String>> values) {
        setEntityValuesWithLang(values, LangFieldType.ENG);
    }
    
    public void setSpaValuesByFieldName(Map<String, List<String>> values) {
        setEntityValuesWithLang(values, LangFieldType.SPA);
    }
    
    public void setFraValuesByFieldName(Map<String, List<String>> values) {
        setEntityValuesWithLang(values, LangFieldType.FRA);
    }    

    @Override
    public Set<String> getFieldNames() {
        return entityValuesByFieldName.keySet();
    }

    @Override
    public List<EntityFieldValue> getOccurrencesByFieldName(String fieldName) {
        return entityValuesByFieldName.getOrDefault(fieldName, new ArrayList<EntityFieldValue>());
    }

    @Override
    public Map<String, List<EntityFieldValue>> getFieldOccurrenceMap() {
        return entityValuesByFieldName;
    }

    @Override
    public IEntity addFieldOccurrences(String fieldName, List<EntityFieldValue> list) {
        for (EntityFieldValue e : list) {
            addFieldOccurrence(fieldName, e);
        }
        return this;
    }

    @Override
    public IEntity addFieldOccurrence(String fieldName, EntityFieldValue value) {
        if (!entityValuesByFieldName.containsKey(fieldName)) {
            entityValuesByFieldName.put(fieldName, new LinkedList<EntityFieldValue>());
        }
        entityValuesByFieldName.get(fieldName).add(value);
        return this;
    }

    @Override
    public IEntity addDateFieldOccurrence(String fieldName, EntityFieldValue value, DateTimeFormatter formatter) {
        List<EntityFieldValue> list = new LinkedList<>();
        list.add(value);
        return addDateFieldOccurrences(fieldName, list, formatter);
    }

    @Override
    public IEntity addDateFieldOccurrence(String fieldName, LocalDateTime value) {
        ZoneOffset zoneOffSet = ZoneOffset.UTC;
        List<EntityFieldValue> list = new LinkedList<>();
        list.add(new EntityFieldValue.Builder()
                .fromValue(DateTimeFormatter.ISO_INSTANT.format(value.toInstant(zoneOffSet))).build());
        return this.addDateFieldOccurrences(fieldName, list, DateTimeFormatter.ISO_INSTANT);
    }

    @Override
    public IEntity addDateFieldOccurrences(String fieldName, List<EntityFieldValue> values, DateTimeFormatter formatter) {
        ZoneOffset zoneOffSet = ZoneOffset.UTC;
        if (formatter == null) {
            formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
        }
        List<String> newList = new LinkedList<>();
        for (EntityFieldValue dateFieldValue : values) {
            LocalDateTime parsedDate = LocalDateTime.parse(dateFieldValue.getValue(), formatter);
            newList.add(DateTimeFormatter.ISO_INSTANT.format(parsedDate.toInstant(zoneOffSet)));
        }
        if (datesByFieldName.containsKey(fieldName)) {
            datesByFieldName.get(fieldName).addAll(newList);
            datesRangeByFieldName.get(fieldName).addAll(newList);
        } else {
            datesByFieldName.put(fieldName, newList);
            datesRangeByFieldName.put(fieldName, newList);
        }
        return this;
    }

    @Override
    public IEntity addSortingFieldOccurrence(String fieldName, EntityFieldValue value) {
        String oldValue = sortValueByFieldName.get(fieldName);
        if (oldValue == null) {
            sortValueByFieldName.put(fieldName, value.getValue());
        }
        return null;
    }

    @Override
    public Set<String> getRelationNames() {
        return identifiersByRelation.keySet();
    }

    @Override
    public List<String> getIdentifiersByRelationName(String relationName) {
        return identifiersByRelation.getOrDefault(relationName, new ArrayList<String>());
    }

    @Override
    public Map<String, List<String>> getRelatedIdentifiersMap() {
        return identifiersByRelation;
    }

    @Override
    public IEntity addRelatedIdentifier(String relationName, String value) {
        List<String> list = identifiersByRelation.getOrDefault(relationName, new ArrayList<String>());
        list.add(value);
        identifiersByRelation.put(relationName, list);
        return this;
    }

    @Override
    public IEntity addRelatedIdentifiers(String relationName, List<String> list) {
        if (!identifiersByRelation.containsKey(relationName)) {
            identifiersByRelation.put(relationName, list);
        } else {
            identifiersByRelation.get(relationName).addAll(list);
        }
        return this;
    }

    @Override
    public IEntity removeFieldOccurrences(String fieldName) {
        entityValuesByFieldName.remove(fieldName);
        return this;
    }

    @Override
    public String getLinkByRelationName(String relationName) {
        return linksByRelation.getOrDefault(relationName, null);
    }

    @Override
    public Map<String, String> getRelatedLinksMap() {
        return linksByRelation;
    }

    @Override
    public IEntity removeRelatedIdentifiers(String fieldName) {
        identifiersByRelation.remove(fieldName);
        return this;
    }

    @Override
    public IEntity addRelatedLink(String relationName, String value) {
        linksByRelation.put(relationName, value);
        return this;
    }

    @Override
    public IEntity removeRelatedLink(String fieldName) {
        linksByRelation.remove(fieldName);
        return this;
    }

    @Override
    public List<String> getSemanticIds() {
        return this.semanticIds;
    }

    @Override
    public IEntity addSemanticId(String id) {
        this.semanticIds.add(id);
        return this;
    }

    @Override
    public List<String> getProvenanceIds() {
        return this.provenanceIds;
    }

    @Override
    public IEntity addProvenanceId(String id) {
        this.provenanceIds.add(id);
        return this;
    }

    private Map<String, List<String>> getEntityValuesByLang(LangFieldType lang) {
        Map<String, List<String>> values = new HashMap<>();
        entityValuesByFieldName.forEach((fieldName, listEntityValues) -> {
            List<String> list = new LinkedList<>();
            for (EntityFieldValue entityValue : listEntityValues) {
                if (lang == entityValue.getLanguage()) {
                    list.add(entityValue.getValue());
                }
            }
            values.put(fieldName, list);
        });
        return values;
    }

    private void setEntityValuesWithLang(Map<String, List<String>> values, LangFieldType lang) {
        for (Map.Entry<String, List<String>> entries : values.entrySet()) {
            for (String value : entries.getValue()) {
                EntityFieldValue entityValue = new EntityFieldValue.Builder().fromValueLang(value, lang).build();
                addFieldOccurrence(entries.getKey(), entityValue);
            }
        }
    }
}