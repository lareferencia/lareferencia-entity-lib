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
import java.util.Set;

import javax.persistence.AssociationOverride;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.jena.sparql.function.library.e;
import org.springframework.data.rest.core.annotation.RestResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Table(name = "entity")
@javax.persistence.Entity
@AssociationOverride( name="semanticIdentifiers",
joinTable=@JoinTable( name = "entity_semantic_identifier", 
		  joinColumns = @JoinColumn(name = "entity_id"), 
		  inverseJoinColumns = @JoinColumn(name = "semantic_id"), 
		  indexes = { @Index(name = "esi_entity_id",  columnList="entity_id", unique = false),
			       @Index(name = "esi_semantic_id",  columnList="semantic_id", unique = false)}
))
public class Entity extends BaseEntity  {

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

	@Setter
	@Getter
	@JsonIgnore
	@Column(name = "dirty")
	private Boolean dirty = true;

	@Column(name="relations", columnDefinition="TEXT")
	@Convert(converter = MultiMapRelationAttributeConverter.class)
	@Setter(AccessLevel.NONE)
	//@Getter(AccessLevel.NONE)
	protected Multimap<String, Relation> relationsByName = LinkedHashMultimap.create();

	/**
	 * Returns occurrences by fieldname
	 * @return
	 */
	@JsonIgnore
	public Collection<Relation> getRelationsByType(String type) {

		Collection<Relation> relations = this.relationsByName.get(type);

		if (relations == null)
			return new HashSet<Relation>();
		else
			return relations;

	}

	@JsonIgnore
	public Collection<String> getRelatioTypes() {
		return this.relationsByName.keySet();
	}

}
