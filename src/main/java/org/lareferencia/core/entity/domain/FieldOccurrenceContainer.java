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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@MappedSuperclass
public abstract class FieldOccurrenceContainer  {

	public static final String FIELDOCCURRENCE_JSON_OCURRENCES_FIELD = "f";

	@Column(name="fieldvalues", columnDefinition="TEXT")
	@Convert(converter = MultiMapFieldOcurrenceAttributeConverter.class)
	@Setter(AccessLevel.NONE)
	@JsonIgnore
	//@Getter(AccessLevel.NONE)
	protected Multimap<String, FieldOccurrence> occurrencessByFieldName = LinkedHashMultimap.create();


	/**
	 * Returns occurrences by fieldname
	 * @return
	 */
	@JsonIgnore
	public Collection<FieldOccurrence> getFieldOccurrences(String fieldName) {

		Collection<FieldOccurrence> occurrences = this.occurrencessByFieldName.get(fieldName);
		if (occurrences == null) 
			return new LinkedHashSet<FieldOccurrence>();
		else
			return occurrences;
	}

	/**
	 * Builds and returns all occurrences as a map of fieldname, occurrences
	 * ATENTION: this method will trigger the loading of all ocurrences into a map, should be avoided in massive tasks
	 * @return
	 */
	@JsonProperty(FIELDOCCURRENCE_JSON_OCURRENCES_FIELD)
	@JsonInclude(Include.NON_EMPTY)
	public Map<String, Collection<FieldOccurrence>> getFieldOccurrencesAsMap() {
		return occurrencessByFieldName.asMap();
	}

	public void addFieldOccurrence(String fieldName, FieldOccurrence occurrence) {
		this.occurrencessByFieldName.put(fieldName, occurrence);
	}
	
	public void removeAllFieldOccurrences() {
		this.occurrencessByFieldName.clear();
	}

	public String toString() {
		return "fields: " + this.getFieldOccurrencesAsMap();
	}	

}
