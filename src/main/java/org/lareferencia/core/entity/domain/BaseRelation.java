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

import java.util.UUID;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@MappedSuperclass
//@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public abstract class BaseRelation<T extends BaseEntity> extends FieldOccurrenceContainer implements Persistable<RelationId> {
	
	@EqualsAndHashCode.Include
	@EmbeddedId
	@Getter
	private RelationId id;
	
	@Getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "relation_type_id", insertable = false, updatable = false)
	private RelationType relationType;
	
	
	/***** Related entities ********/
	@Getter
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_entity_id", insertable = false, updatable = false)
	private T fromEntity;
	
	
	@Getter
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "to_entity_id", insertable = false, updatable = false)
	private T toEntity;
	/****/
		
	public String getRelationTypeName() {
		return relationType.getName();
	}
	
	public BaseRelation(RelationType relationType, T fromEntity, T toEntity ) {
		super();
	
		this.id = new RelationId(relationType.getId(), fromEntity.getId(), toEntity.getId());
		
		this.relationType = relationType;
		this.fromEntity = fromEntity;
		this.toEntity = toEntity;	
			
	}
	
	@JsonIgnore
	public T getRelatedEntity(UUID entityId) {
		
		if ( this.id.fromEntityId.equals(entityId) )
			return this.toEntity;
		else {
			if ( this.id.toEntityId.equals(entityId) )		
				return this.fromEntity;
			 else
				return null; 
		}
	}
	
	public Long getRelationTypeId() {
		return id.relationTypeId;
	}
	
	//////////////////////////Begin of persistable //////////////////////////////////////
	
		
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

		
}
