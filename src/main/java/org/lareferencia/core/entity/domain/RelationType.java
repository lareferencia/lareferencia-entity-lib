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

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Table(name = "relation_type")
@javax.persistence.Entity
public class RelationType extends EntityRelationType {
	

	/***** Related entities ********/
	
	@Getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_entity_id")
	private EntityType fromEntityType;
	
	@Getter
	@EqualsAndHashCode.Include
	@Column(name = "from_entity_id",insertable = false, updatable = false)
	private Long fromEntityTypeId;
	
	@Getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "to_entity_id")
	private EntityType toEntityType;

	@Getter
	@EqualsAndHashCode.Include
	@Column(name = "to_entity_id", insertable = false, updatable = false)
	private Long toEntityTypeId;

	
	/****/
	
	
	public RelationType(String name) {
		super(name);
	}


	public void setFromEntityType(EntityType fromEntityType) {
		this.fromEntityType = fromEntityType;
		this.fromEntityTypeId = fromEntityType.getId();
	}


	public void setToEntityType(EntityType toEntityType) {
		this.toEntityType = toEntityType;
		this.toEntityTypeId = toEntityType.getId();
	}
	
	
	
}
