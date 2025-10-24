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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = EntityJsonSerializer.class, as=IEntity.class)
public interface IEntity {
		
	public String getId();
	public String getType();
	
	public List<String> getSemanticIds();
	public IEntity addSemanticId(String id);
	
	public List<String> getProvenanceIds();
	public IEntity addProvenanceId(String id);
	
	// fields
	public Set<String> getFieldNames();
	public List<EntityFieldValue> getOccurrencesByFieldName(String fieldName);
	public Map<String, List<EntityFieldValue>> getFieldOccurrenceMap();

	public IEntity addFieldOccurrence(String fieldName, EntityFieldValue value);
	public IEntity addFieldOccurrences(String fieldName, List<EntityFieldValue> value);
	public IEntity removeFieldOccurrences(String fieldName);
    public IEntity addDateFieldOccurrence(String fieldName, EntityFieldValue value, DateTimeFormatter formatter);
    public IEntity addDateFieldOccurrence(String fieldName, LocalDateTime value);
    public IEntity addDateFieldOccurrences(String fieldName, List<EntityFieldValue> values, DateTimeFormatter formatter);
	
	public IEntity addSortingFieldOccurrence(String fieldName, EntityFieldValue value);

	// related entity identifiers and links
	public Set<String> getRelationNames();
	
	public List<String> getIdentifiersByRelationName(String relationName);
	public Map<String, List<String>> getRelatedIdentifiersMap();

	public String getLinkByRelationName(String relationName);
	public Map<String, String> getRelatedLinksMap();

	public IEntity addRelatedIdentifier(String relationName, String value);
	public IEntity addRelatedIdentifiers(String relationName, List<String> value);
	public IEntity removeRelatedIdentifiers(String fieldName);


	public IEntity addRelatedLink(String relationName, String value);
	public IEntity removeRelatedLink(String fieldName);	
}
