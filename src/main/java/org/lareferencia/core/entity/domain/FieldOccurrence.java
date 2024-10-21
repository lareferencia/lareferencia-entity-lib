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
import java.util.Map;

import org.lareferencia.core.util.hashing.XXHash64Hashing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonDeserialize(using = FieldOccurrenceDeserializer.class)
@NoArgsConstructor
@Data
public abstract class FieldOccurrence {

	public static final String FIELDOCCURRENCE_JSON_VALUE_FIELD = "v";
	public static final String FIELDOCCURRENCE_JSON_LANG_FIELD = "l";
	public static final String FIELDOCCURRENCE_JSON_PREFERRED_FIELD = "p";

	public static final String NO_LANG = null;

	@JsonInclude(Include.NON_DEFAULT)
	@EqualsAndHashCode.Include
	@Setter(AccessLevel.NONE)
	@JsonProperty(FIELDOCCURRENCE_JSON_LANG_FIELD)
	protected String lang = NO_LANG;

	@JsonInclude(Include.NON_DEFAULT)
	@EqualsAndHashCode.Include
	@Setter(AccessLevel.NONE)
	@JsonProperty(FIELDOCCURRENCE_JSON_PREFERRED_FIELD)
	protected Boolean preferred = false;

	public abstract Object getContent();

	public abstract String getValue(String... params) throws EntityRelationException;


	public FieldOccurrence setLang(String lang) {
		this.lang = lang;
		return this;
	}

	public FieldOccurrence setPreferred(Boolean preferred) {
		this.preferred = preferred;
		return this;
	}

	@Override
	public String toString() {
		// only add preferred if it is true, this to preserve backward compatibility
		return (lang!= null ? (lang + "::") : "") + getContent() + (preferred ? ( "::" + preferred ) : "");
	}

	public Long hashCodeLong() {
		return XXHash64Hashing.calculateHashLong(this.toString());
	}

	@Override
	public boolean equals(Object obj) {

		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;

		FieldOccurrence other = (FieldOccurrence) obj;

		if (preferred == null) {
			if (other.preferred != null)
				return false;
		} else if (!preferred.equals(other.preferred))
			return false;

		if (lang == null) {
			if (other.lang != null)
				return false;
		} else if (!lang.equals(other.lang))
			return false;

		if (this.getContent() == null) {
			if (other.getContent() != null)
				return false;
		} else if (!this.getContent().equals(other.getContent()))
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lang == null) ? 0 : lang.hashCode());

		// only add preferred if it is true, this to preserve backward compatibility
		if ( preferred )
			result = prime * result + ((preferred == null) ? 0 : preferred.hashCode());

		return result;
	}

	public static SimpleFieldOccurrence createSimpleFieldOccurrence(String value)  {
		SimpleFieldOccurrence occr = new SimpleFieldOccurrence(value);
		return occr;
	}

	public static ComplexFieldOccurrence createComplexFieldOccurrence() {
		ComplexFieldOccurrence occr = new ComplexFieldOccurrence();
		return occr;
	}

	public static ComplexFieldOccurrence createComplexFieldOccurrence(Map<String,String> value)  {
		ComplexFieldOccurrence occr = new ComplexFieldOccurrence(value);
		return occr;
	}

}
