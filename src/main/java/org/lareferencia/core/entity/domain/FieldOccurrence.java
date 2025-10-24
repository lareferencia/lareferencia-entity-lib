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

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.lareferencia.core.util.hashing.XXHash64Hashing;
import org.springframework.data.domain.Persistable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@jakarta.persistence.Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
@Table(name = "field_occurrence" /*
									 * , indexes = { @Index(name = "field_occr_by_type_and_container",
									 * columnList="field_type_id,field_container_id", unique = false) }
									 */)
public abstract class FieldOccurrence extends CacheableEntityBase<Long> {

	public static final String NO_LANG = null;

	@Setter(AccessLevel.NONE)
	@Id
	// @GeneratedValue(generator = "FieldOccurrenceTypeSequenceGenerator")
	// @SequenceGenerator(name="FieldOccurrenceTypeSequenceGenerator",sequenceName="field_occr_type_seq",
	// allocationSize=100)
	private Long id = null;

	// field type
	@Setter(AccessLevel.NONE)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "field_type_id")
	protected FieldType fieldType;

	/*
	 * This field is used for include fieldtype in hash and equals without executing
	 * the join
	 */
	@EqualsAndHashCode.Include
	@Setter(AccessLevel.NONE)
	@JsonIgnore
	@Column(name = "field_type_id", insertable = false, updatable = false)
	private Long fieldTypeId;

	@JsonInclude(Include.NON_NULL)
	@EqualsAndHashCode.Include
	protected String lang = null;

	@JsonInclude(Include.NON_NULL)
	@EqualsAndHashCode.Include
	protected Boolean preferred = false;

	@JsonIgnore
	public String getFieldName() {
		return fieldType.getName();
	}

	public FieldOccurrence(FieldType fieldType) {
		super();
		this.fieldType = fieldType;
		this.fieldTypeId = fieldType.getId();
		this.lang = NO_LANG;
		this.preferred = false;
	}

	public abstract Object getContent();

	public abstract FieldOccurrence addValue(String... params) throws EntityRelationException;

	public abstract String getValue(String... params) throws EntityRelationException;

	@Override
	public String toString() {
		// only add preferred if it is true, this to preserve backward compatibility
		return fieldTypeId + "::" + lang + "::" + getContent() + (preferred ? ( "::" + preferred ) : "");
	}

	public Long hashCodeLong() {
		return XXHash64Hashing.calculateHashLong(this.toString());
	}

	@PrePersist
	public void updateId() {
		if (id == null)
			id = hashCodeLong();
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

		if (fieldTypeId == null) {
			if (other.fieldTypeId != null)
				return false;
		} else if (!fieldTypeId.equals(other.fieldTypeId))
			return false;

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
		result = prime * result + ((fieldTypeId == null) ? 0 : fieldTypeId.hashCode());
		result = prime * result + ((lang == null) ? 0 : lang.hashCode());

		// only add preferred if it is true, this to preserve backward compatibility
		if ( preferred )
			result = prime * result + ((preferred == null) ? 0 : preferred.hashCode());

		return result;
	}

}
