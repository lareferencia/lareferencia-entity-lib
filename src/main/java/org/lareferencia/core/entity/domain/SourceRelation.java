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

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Table(name = "source_relation", indexes = { @Index(name = "source_relation_type_members",  columnList="relation_type_id,from_entity_id,to_entity_id", unique = false) } )
@jakarta.persistence.Entity
@AssociationOverride( name="occurrences",
joinTable=@JoinTable(name = "source_relation_fieldoccr", 
					   joinColumns = {@JoinColumn(name = "from_entity_id"), @JoinColumn(name = "relation_type_id"), @JoinColumn(name = "to_entity_id")}, 
					   inverseJoinColumns = @JoinColumn(name = "fieldoccr_id")))
public class SourceRelation extends BaseRelation<SourceEntity> {
	
	
//	/***** Related entities ********/
//	@Getter
//	@JsonIgnore
//	@Column(name = "from_final_entity_id")
//	private UUID fromFinalEntityId;
//	
//	
//	@Getter
//	@JsonIgnore
//	@Column(name = "to_final_entity_id")
//	private UUID toFinalEntityId;
//
//	@Getter
//	@JsonIgnore
//	@ManyToOne(fetch = FetchType.LAZY)
//	@JoinColumn(name = "from_final_entity_id", insertable = false, updatable = false)
//	private Entity fromFinalEntity;
//		
//	@Getter
//	@JsonIgnore
//	@ManyToOne(fetch = FetchType.LAZY)
//	@JoinColumn(name = "to_final_entity_id", insertable = false, updatable = false)
//	private Entity toFinalEntity;

//	public void setFromFinalEntity(Entity fromFinalEntity) {
//		this.fromFinalEntity = fromFinalEntity;
//		this.fromFinalEntityId = fromFinalEntity.getId();
//	}
//
//	public void setToFinalEntity(Entity toFinalEntity) {
//		this.toFinalEntity = toFinalEntity;
//		this.toFinalEntityId = toFinalEntity.getId();
//	}
	
	
	/****/
	
	
	@Getter @Setter
	private Date startDate;
	
	@Getter @Setter
	private Date endDate;
	
	@Getter @Setter
	private double confidence = 1.0;


	public SourceRelation() {
		super();
	}

	public SourceRelation(RelationType relationType, SourceEntity fromEntity, SourceEntity toEntity) {
		super(relationType, fromEntity, toEntity);
	}

	
	
	
	
}
