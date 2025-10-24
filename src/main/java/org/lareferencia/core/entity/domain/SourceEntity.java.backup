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

import javax.persistence.AssociationOverride;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.lareferencia.core.util.hashing.XXHash64Hashing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.Setter;

@javax.persistence.Entity
@Table(name = "source_entity", 
indexes = { @Index(name = "idx_final_entity_id",  columnList="final_entity_id", unique = false) })
@AssociationOverride( name="occurrences",
joinTable=@JoinTable(name = "source_entity_fieldoccr", 
					   joinColumns = @JoinColumn(name = "entity_id"), 
					   inverseJoinColumns = @JoinColumn(name = "fieldoccr_id"), 
					   indexes = { @Index(name = "sfo_entity_id",  columnList="entity_id", unique = false),
						       @Index(name = "sfo_fieldoccr_id",  columnList="fieldoccr_id", unique = false)}))
@AssociationOverride( name="semanticIdentifiers",
joinTable=@JoinTable( name = "source_entity_semantic_identifier", 
		  joinColumns = @JoinColumn(name = "entity_id"), 
		  inverseJoinColumns = @JoinColumn(name = "semantic_id"), 
		  indexes = { @Index(name = "ssi_entity_id",  columnList="entity_id", unique = false),
			       @Index(name = "ssi_semantic_id",  columnList="semantic_id", unique = false)}
))
public class SourceEntity extends BaseEntity<SourceRelation>  {

			
	public SourceEntity() {
		super();
	}

	public SourceEntity(EntityType type, Provenance provenance) {
		super(type);
		this.provenance = provenance;
		this.provenanceId = provenance.getId();
	}

	@Getter
	@JsonInclude(Include.NON_NULL)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "final_entity_id")
	private Entity finalEntity;
	
	@JsonIgnore
	@Getter
	@Column(name = "final_entity_id", insertable = false, updatable = false)
	protected UUID finalEntityId;
	
	@Setter
	@Getter
	@Column(name = "deleted")
	private Boolean deleted = false;
	
	@Getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "provenance_id")
	protected Provenance provenance;

	@JsonIgnore
	@Getter
	@Column(name = "provenance_id", insertable = false, updatable = false)
	protected Long provenanceId;
	
	public Long hashCodeLong() {
		return XXHash64Hashing.calculateHashLong( this.toString() );
	}

	public void setFinalEntity(Entity finalEntity) {
		this.finalEntity = finalEntity;
		this.finalEntityId = finalEntity.getId();
	}

//	@Override
//	public String toString() {
//		return entityTypeId + "::" + sourceId + "::" + recordId + "::" + itemId;
//	}
//	

}
