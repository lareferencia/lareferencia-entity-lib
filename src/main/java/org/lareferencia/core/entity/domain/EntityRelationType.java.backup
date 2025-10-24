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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.NaturalId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@javax.persistence.Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Table(name = "entity_relation_type")
public abstract class EntityRelationType extends CacheableEntityBase<Long> implements ICacheableNamedEntity<Long>{
	
	@Override
	public String toString() {
		return "EntityRelationType [id=" + id + ", name=" + name + "]";
	}

	@Getter
	@Id
	@GeneratedValue(generator = "ERTypeSequenceGenerator")	
	@SequenceGenerator(name="ERTypeSequenceGenerator",sequenceName="entity_relation_type_seq", allocationSize=100)
	@EqualsAndHashCode.Include
	private final Long id = null;
	
	@Getter
	@NaturalId
	@EqualsAndHashCode.Include
	private String name;
	
	@Getter @Setter
	private String description;
	
	
	public EntityRelationType(String name) {
		super();
		this.name = name;
	}
	
	@OneToMany(mappedBy="entityRelationType", cascade = {CascadeType.ALL}, fetch=FetchType.EAGER)
	@MapKeyColumn(name="name")
	private Map<String, FieldType> fields = new HashMap<String, FieldType>();
	
	public void addField(FieldType field) {
		//this.fields(field);
		this.fields.put(field.getName(), field);
		field.setEntityRelationType(this);
	}
	
	/**
	 * Return names of first level entity fields
	 * @return List<String>
	 */
	public List<String> getFieldNames() {
		return new ArrayList<String>( fields.keySet() );
	}
	
	/**
	 * Return names of first level entity fields
	 * @return List<String>
	 */
	public List<FieldType> getFields() {
		return new ArrayList<FieldType>( fields.values() );
	}
	
	/**
	 * Return FieldType by name
	 * @return FieldType
	 * @throws EntityRelationException 
	 * 
	 * 
	 */
	public FieldType getFieldByName(String fieldName) throws EntityRelationException {
		
		FieldType type = fields.get(fieldName);
		
		if ( type == null )
			throw new EntityRelationException("EntityType: " + this.getName() + " doesn´t contain FieldType named:" + fieldName);
		
		return type;
	}
	
}
