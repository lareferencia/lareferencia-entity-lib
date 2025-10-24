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
import javax.persistence.Id;

import org.lareferencia.core.util.hashing.XXHash64Hashing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@javax.persistence.Entity
@javax.persistence.Table(name = "semantic_identifier")
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class SemanticIdentifier extends CacheableEntityBase<Long>  {

	@Id
	@Getter
	//@Column(columnDefinition = "BIGINT UNSIGNED")
	@EqualsAndHashCode.Include
	private Long id;
	
	@Getter
	@Column(name = "semantic_id")
	private String identifier;
		
	public SemanticIdentifier(String semanticId) { 
		this.identifier = semanticId;
		this.id = hashCodeLong();
	}
	
	@Override
	public String toString() {
		return identifier;	
	}

	public Long hashCodeLong() {
		return XXHash64Hashing.calculateHashLong( this.identifier );
	}		
}
