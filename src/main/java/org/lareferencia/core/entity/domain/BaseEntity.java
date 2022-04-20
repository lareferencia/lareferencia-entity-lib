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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.Transient;

import org.springframework.data.domain.Persistable;
import org.springframework.data.rest.core.annotation.RestResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@MappedSuperclass
//@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class BaseEntity<T extends BaseRelation> extends FieldOccurrenceContainer implements Persistable<UUID>  {
	
	
	@Setter(AccessLevel.NONE)
	@Getter
	@Id
    //@GeneratedValue(generator = "system-uuid")
    //@GenericGenerator(name = "system-uuid", strategy = "uuid2")
	@Column(name = "uuid", unique = true, nullable = false, insertable = true, updatable = false)
	@EqualsAndHashCode.Include
	protected UUID id; // this way ensures that the uuid will created with the object and not at persistence. Solves linking entities before persistence.
	

	/** Defaul constructor creates an UUID */ 
	public BaseEntity() {
		id = UUID.randomUUID();
		neverPersisted = true;
	}
	
	public BaseEntity(EntityType type) {
		id = UUID.randomUUID();
		neverPersisted = true;
		this.entityType = type;
	}
	
	////////////////////////// Begin of persistable //////////////////////////////////////

	
	/**
	 * This is part of Persistable interface, and allows to Hibernate not to look for this id in the database, speeds ups persistences by avoiding unesesary queries for existint UUID.
	 */
	@Override
	@JsonIgnore
	public boolean isNew() {
		return neverPersisted;
	}
	
	/** By default on instance creation, neverPersisted is marked as true */
	@Transient
	@JsonIgnore
	@Setter(AccessLevel.NONE)
	@Getter(AccessLevel.NONE)
	private boolean neverPersisted=true;
	
	
	/** On Entity load neverPersisted and dirty are marked as false */ 
	@PostLoad
	void onPostLoad() {
		neverPersisted=false;
		//dirty=false;
	}
	
	/** After the persistence is completed successfully the object is marked as persisted and not dirty */
	@PostPersist
	void onPostPersist() {
		neverPersisted=false;
		//dirty=false;
	}
	
	////////////////////////// End of persistable //////////////////////////////////////
	
		
	@Getter
	@Setter(AccessLevel.NONE)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "entity_type_id")
	protected EntityType entityType;

	@JsonIgnore
	@Setter(AccessLevel.NONE)
	@Getter
	@Column(name = "entity_type_id", insertable = false, updatable = false)
	protected Long entityTypeId;
			
	@ManyToMany(cascade = {CascadeType.DETACH}, fetch = FetchType.LAZY)
	protected Set<SemanticIdentifier> semanticIdentifiers = new HashSet<SemanticIdentifier>();
	
	
	@RestResource(exported = false)
	@Getter
	@OneToMany(mappedBy = "fromEntity", fetch = FetchType.LAZY)
	private Set<T> fromRelations = new HashSet<T>();
	
	@RestResource(exported = false)
	@Getter
	@OneToMany(mappedBy = "toEntity", fetch = FetchType.LAZY)
	private Set<T> toRelations = new HashSet<T>();
	
	
	/**
	 * List field names of associated entity type
	 * @return List of field names
	 */
	@JsonIgnore
	public List<String> getFieldNames() {
		return entityType.getFieldNames();
	}
	
	/**
	 * Return entity type name
	 * @return EntityType name
	 */
	public String getEntityTypeName() {
		return entityType.getName();
	}
	
	
	public void addSemanticIdentifier(SemanticIdentifier semanticIdentifier) {
		
		/** Check if this identifiers was not linked to this entity before */
		if ( ! semanticIdentifiers.contains( semanticIdentifier ) ){
			this.semanticIdentifiers.add( semanticIdentifier );
//			markAsDirty(); // the entity was actually updated
		}
	}
	
	public void addSemanticIdentifiers(Collection<SemanticIdentifier> semanticIdentifiers) {
		
		for ( SemanticIdentifier semId : semanticIdentifiers )
			this.addSemanticIdentifier(semId);
	}
	
	
	public void removeAllSemanticIdentifiers() {
		this.semanticIdentifiers.clear();
	}
	
	@JsonIgnore
	public Boolean hasSemanticId(SemanticIdentifier semanticIdentifier) {
		return this.semanticIdentifiers.contains( semanticIdentifier );
	}
	

	@JsonProperty("semanticIdentifiers")
	public Collection<SemanticIdentifier> getSemanticIdentifiers() {
		return this.semanticIdentifiers;
	}
	
		
	@Override
	public String toString() {
		return "Entity [Id=" + getId() + "]";
	}

}
