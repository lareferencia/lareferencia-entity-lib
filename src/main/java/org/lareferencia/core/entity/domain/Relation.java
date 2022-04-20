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

import javax.persistence.AssociationOverride;
import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "relation", indexes = { @Index(name = "relation_type_members",  columnList="relation_type_id,from_entity_id,to_entity_id", unique = false) } )
@javax.persistence.Entity
@AssociationOverride( name="occurrences",
joinTable=@JoinTable(name = "relation_fieldoccr", 
					   joinColumns = {@JoinColumn(name = "from_entity_id"), @JoinColumn(name = "relation_type_id"), @JoinColumn(name = "to_entity_id")}, 
					   inverseJoinColumns = @JoinColumn(name = "fieldoccr_id")))
public class Relation extends BaseRelation<Entity> {

	public Relation() {
		super();
	}

	public Relation(RelationType relationType, Entity fromEntity, Entity toEntity) {
		super(relationType, fromEntity, toEntity);
	}
	
	@Setter
	@Getter
	@JsonIgnore
	@Column(name = "dirty")
	private Boolean dirty = true;
	



}
