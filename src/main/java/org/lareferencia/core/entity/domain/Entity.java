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
import java.util.UUID;

import javax.persistence.AssociationOverride;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.springframework.data.rest.core.annotation.RestResource;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Table(name = "entity")
@javax.persistence.Entity
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
public class Entity extends BaseEntity<Relation> implements ICacheableEntity<UUID> { 

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

	

	/**
	 * This is part of Persistable interface, and allows to Hibernate not to look
	 * for this id in the database, speeds ups persistences by avoiding unesesary
	 * queries for existint UUID.
	 */
	@Override
	@JsonIgnore
	public boolean isNew() {
		return _isNew;
	}
	
	/** By default on instance creation, neverPersisted is marked as true */
	@Transient
	@JsonIgnore
	@Setter(AccessLevel.NONE)
	@Getter(AccessLevel.NONE)
	private boolean _isNew = true;

	/** On Entity load and persisted is marked as not new (already present in the database) */
	@PostLoad
	@PostPersist
	public void markAsStored() {
		_isNew = false;
	}


	
	

}
