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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.lareferencia.core.util.ListAttributeConverter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "field_type")
@jakarta.persistence.Entity
public class FieldType implements Serializable, ICacheableNamedEntity<Long> {
	
	static public enum Kind { SIMPLE, COMPLEX };

	@Setter(AccessLevel.NONE)
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private final Long id = null;
	
	@EqualsAndHashCode.Include
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "entity_relation_type_id")
	private EntityRelationType entityRelationType;

	@EqualsAndHashCode.Include
	private String  name;
	
	private String  description;
	private Integer maxOccurs = 1;
	private Kind kind = Kind.SIMPLE;

	public FieldType(String fieldname) {
		super();
		this.name = fieldname;
	}
	
	public boolean isSubfield(String fieldName) {
		return subfields.contains(fieldName);
	}

	@Getter
	@Column(name="subfields", columnDefinition="TEXT")
	@Convert(converter = ListAttributeConverter.class)
	private List<String> subfields = new ArrayList<String>();
	
	public Boolean hasSubfields() { return kind != Kind.SIMPLE; }

	public FieldOccurrence buildFieldOccurrence() {
		
		switch (kind) {
		
		case SIMPLE:
			return new SimpleFieldOccurrence(this);
			
		case COMPLEX:
			return new ComplexFieldOccurrence(this);
			
		default:
			return null;
		}
	}
	public void addSubfield(String fieldName) {
		this.subfields.add(fieldName);
	}

	@Transient
	private Boolean isStored = false;

	@Override
	public void markAsStored() {
		isStored = true;
	}
}
