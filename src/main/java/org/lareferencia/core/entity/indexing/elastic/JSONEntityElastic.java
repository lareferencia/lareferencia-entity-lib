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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JSONEntityElastic  {
	
	public JSONEntityElastic(String id, String type) {
		
		super();

		this.id = id;
		this.type = type;
		
	}
	
	protected String id;
	protected List<String> semanticIds = new ArrayList<String>();
	protected List<String> provenanceIds = new ArrayList<String>();
	protected String type;
	
	protected SetMultimap<String, String> occrsByFieldName =  HashMultimap.create();
	protected Multimap<String, JSONEntityElastic> entitiesByRelatedName = ArrayListMultimap.create();

	public Set<String> getFieldNames() {
		return occrsByFieldName.keySet();
	}

	public Collection<String> getOccurrencesByFieldName(String fieldName) {
		return occrsByFieldName.get(fieldName);
	}


	public Map<String, Collection<String>> getFieldOccurrenceMap() {
		return occrsByFieldName.asMap();
	}


	public Set<String> getRelationNames() {
		return entitiesByRelatedName.keySet();
	}

	public Collection<JSONEntityElastic> getRelatedEntitiesByRelationName(String relationName) {
		return entitiesByRelatedName.get(relationName);
	}


	public Map<String, Collection<JSONEntityElastic>> getRelatedEntitiesMap() {
		return entitiesByRelatedName.asMap();
	}

	public void addFieldOccurrence(String fieldName, String value) {
		this.occrsByFieldName.put(fieldName, value);
	}

	public void addRelatedEntity(String relationName, JSONEntityElastic entity) {
		this.entitiesByRelatedName.put(relationName, entity);
	}
	
	public List<String> getSemanticIds() {
		return this.semanticIds;
	}


	public void addSemanticId(String id) {
		this.semanticIds.add(id);
	}

	public List<String> getProvenanceIds() {
		return this.provenanceIds;
	}


	public void addProvenanceId(String id) {
		this.provenanceIds.add(id);
	}


    
}
