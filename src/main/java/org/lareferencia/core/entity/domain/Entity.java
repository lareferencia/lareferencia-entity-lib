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

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.springframework.data.rest.core.annotation.RestResource;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Table(name = "entity")
@jakarta.persistence.Entity
@AssociationOverride( name="occurrences",
joinTable=@JoinTable(name = "entity_fieldoccr", 
					   joinColumns = @JoinColumn(name = "entity_id"), 
					   inverseJoinColumns = @JoinColumn(name = "fieldoccr_id"), 
					   indexes = { @Index(name = "efo_entity_id",  columnList="entity_id", unique = false),
							       @Index(name = "efo_fieldoccr_id",  columnList="fieldoccr_id", unique = false)}
))
@AssociationOverride( name="semanticIdentifiers",
joinTable=@JoinTable( name = "entity_semantic_identifier", 
		  joinColumns = @JoinColumn(name = "entity_id"), 
		  inverseJoinColumns = @JoinColumn(name = "semantic_id"), 
		  indexes = { @Index(name = "esi_entity_id",  columnList="entity_id", unique = false),
			       @Index(name = "esi_semantic_id",  columnList="semantic_id", unique = false)}
))
public class Entity extends BaseEntity<Relation>   {

	public static final String NAME = "entity";
	
    public Entity() {
		super();
	}

	public Entity(EntityType type) {
		super(type);
	}
	
	
	@JsonIgnore
	@RestResource(exported = false)
	@Getter
	@OneToMany(mappedBy = "finalEntity", fetch = FetchType.LAZY)
	private Set<SourceEntity> sourceEntities = new HashSet<SourceEntity>();
	
	@JsonIgnore
	@Getter
	@RestResource(rel="fromRelations",  path="fromRelations", exported = false)
	@OneToMany(mappedBy = "fromEntity", fetch = FetchType.LAZY)
	private Set<Relation> fromRelations = new HashSet<Relation>();
	
	@JsonIgnore
	@Getter
	@RestResource(rel="toRelations",  path="toRelations", exported = false)
	@OneToMany(mappedBy = "toEntity", fetch = FetchType.LAZY)
	private Set<Relation> toRelations = new HashSet<Relation>();
		
	@Setter
	@Getter
	@JsonIgnore
	@Column(name = "dirty")
	private Boolean dirty = true;
	

}
