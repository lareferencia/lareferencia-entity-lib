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

import javax.persistence.Id;

import org.lareferencia.core.entity.indexing.service.EntityFieldValue;
import org.lareferencia.core.entity.indexing.service.IEntity;
import org.lareferencia.core.entity.indexing.service.EntityFieldValue.LangFieldType;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.AccessType.Type;
import org.springframework.data.solr.core.mapping.Dynamic;
import org.springframework.data.solr.core.mapping.Indexed;
import org.springframework.data.solr.core.mapping.SolrDocument;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SolrDocument(collection = EntitySolr.COLLECTION)
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
    @Indexed(name = "id", type = "string")
    protected String id;

    @Indexed(name = "semantic_id", type = "string")
    protected List<String> semanticIds = new ArrayList<String>();

    @Indexed(name = "provenance_id", type = "string")
    protected List<String> provenanceIds = new ArrayList<String>();

    @Indexed(name = TYPE_FIELD_NAME, type = "string")
    protected String type;

    @Dynamic
    @Indexed(name = DYNAMIC_DATE_PREFIX + "*", type = "date")
    protected Map<String, List<String>> datesByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_DATERANGE_PREFIX + "*", type = "date")
    protected Map<String, List<String>> datesRangeByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_FIELD_PREFIX + "*", type = "string")
    protected @AccessType(Type.PROPERTY) Map<String, List<String>> valuesByFieldName = new HashMap<String, List<String>>();

    /**
     * Entity field value map
     */
    protected Map<String, List<EntityFieldValue>> entityValuesByFieldName = new HashMap<String, List<EntityFieldValue>>();

    @Dynamic
    @Indexed(name = DYNAMIC_POR_FIELD_PREFIX + "*", type = "string")
    protected @AccessType(Type.PROPERTY) Map<String, List<String>> porValuesByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_ENG_FIELD_PREFIX + "*", type = "string")
    protected @AccessType(Type.PROPERTY) Map<String, List<String>> engValuesByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_SPA_FIELD_PREFIX + "*", type = "string")
    protected @AccessType(Type.PROPERTY) Map<String, List<String>> spaValuesByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_FRA_FIELD_PREFIX + "*", type = "string")
    protected @AccessType(Type.PROPERTY) Map<String, List<String>> fraValuesByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_UND_FIELD_PREFIX + "*", type = "string")
    protected @AccessType(Type.PROPERTY) Map<String, List<String>> undValuesByFieldName = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_SORT_FIELD_PREFIX + "*", type = "string")
    protected Map<String, String> sortValueByFieldName = new HashMap<String, String>();

    @Dynamic
    @Indexed(name = DYNAMIC_RELID_PREFIX + "*", type = "string")
    protected Map<String, List<String>> identifiersByRelation = new HashMap<String, List<String>>();

    @Dynamic
    @Indexed(name = DYNAMIC_LINK_PREFIX + "*", type = "string")
    protected Map<String, String> linksByRelation = new HashMap<String, String>();

    public void setValuesByFieldName(Map<String, List<String>> values) {
        valuesByFieldName = values;
    }

    public Map<String, List<String>> getValuesByFieldName() {
        Map<String, List<String>> values = new HashMap<String, List<String>>();
        entityValuesByFieldName.forEach((fieldName, listEntityValues) -> {
            List<String> list = new LinkedList<String>();
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
            // TODO: to be optimized
            addFieldOccurrence(fieldName, e);
        }

        return this;
    }

    @Override
    public IEntity addFieldOccurrence(String fieldName, EntityFieldValue value) {

        if (!entityValuesByFieldName.containsKey(fieldName)) {
            entityValuesByFieldName.put(fieldName,new LinkedList<EntityFieldValue>());
        }
        entityValuesByFieldName.get(fieldName).add(value);

        return this;
    }

    @Override
    public IEntity addDateFieldOccurrence(String fieldName, EntityFieldValue value, DateTimeFormatter formatter) {
        List<EntityFieldValue> list = new LinkedList<EntityFieldValue>();

        list.add(value);

        return addDateFieldOccurrences(fieldName, list, formatter);
    }

    @Override
    public IEntity addDateFieldOccurrence(String fieldName, LocalDateTime value) {
        // Default zoneOffset (probably should be configured)
        ZoneOffset zoneOffSet = ZoneOffset.UTC;
        List<EntityFieldValue> list = new LinkedList<>();
        list.add(new EntityFieldValue.Builder()
                .fromValue(DateTimeFormatter.ISO_INSTANT.format(value.toInstant(zoneOffSet))).build());
        return this.addDateFieldOccurrences(fieldName, list, DateTimeFormatter.ISO_INSTANT);
    }

    @Override
    public IEntity addDateFieldOccurrences(String fieldName, List<EntityFieldValue> values,
            DateTimeFormatter formatter) {
        // Default zoneOffset (probably should be configured)
        ZoneOffset zoneOffSet = ZoneOffset.UTC;

        // By default the formatter is ISO_ZONED_DATE_TIME -
        // '2011-12-03T10:15:30+01:00[Europe/Paris]'
        if (formatter == null) {
            formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
        }

        List<String> newList = new LinkedList<String>();
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

        if (oldValue == null) // only if not value was added
            sortValueByFieldName.put(fieldName, value.getValue());

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
        if (!identifiersByRelation.containsKey(relationName))
            identifiersByRelation.put(relationName, list);
        else
            identifiersByRelation.get(relationName).addAll(list);

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
        Map<String, List<String>> values = new HashMap<String, List<String>>();
        entityValuesByFieldName.forEach((fieldName, listEntityValues) -> {
            List<String> list = new LinkedList<String>();
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
