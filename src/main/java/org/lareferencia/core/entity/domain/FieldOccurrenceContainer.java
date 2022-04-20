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
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;
import javax.persistence.ManyToMany;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


//@Cacheable
//@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@MappedSuperclass
public abstract class FieldOccurrenceContainer  {

	
//	/**
//	 *  This is a high level implementation of a dirty state, it allows to track when an entity was actually updated, it avoids unnecessary updates, should be provided by hibernate but for some reason its not working at lowlevel
//	 */
//
//	/** Initialy dirty is true, since was never persisted */
//	@Transient
//	@JsonIgnore
//	@Setter(AccessLevel.NONE)
//	@Getter(AccessLevel.NONE)
//	protected Boolean dirty = true;
//	
//	@JsonIgnore
//	protected void markAsDirty() {
//		dirty = true;
//	}
//	
//	public Boolean isDirty() {
//		return dirty;
//	}


	// this member must not have public getter/setter
	@JsonIgnore
	@ManyToMany(cascade = {CascadeType.MERGE}, fetch = FetchType.LAZY)
	protected Set<FieldOccurrence> occurrences = new HashSet<FieldOccurrence>();

	@Transient
	@Setter(AccessLevel.NONE)
	@Getter(AccessLevel.NONE)
	protected Multimap<String, FieldOccurrence> occurrencessByFieldName;

	/**
	 * Loads plain list of occurrences into a complex map structure
	 */
	private void loadOccurrencesIntoMultimap() {
		
		if ( occurrencessByFieldName == null ) {
			occurrencessByFieldName = ArrayListMultimap.create();
			for (FieldOccurrence occr : occurrences) 
				occurrencessByFieldName.put(occr.getFieldName(), occr);
		}
	}
	
	/**
	 * Builds and returns occurrences by fieldname
	 * ATENTION: this method will trigger the loading of all ocurrences into a map, should be avoided in massive tasks 
	 * @return
	 */
	@JsonIgnore
	public Collection<FieldOccurrence> getFieldOccurrences(String fieldName) {
		loadOccurrencesIntoMultimap();
		return this.occurrencessByFieldName.get(fieldName);
	}
	
	/**
	 * Builds and returns all occurrences as a map of fieldname, occurrences
	 * ATENTION: this method will trigger the loading of all ocurrences into a map, should be avoided in massive tasks 
	 * @return
	 */
	@JsonProperty("fields")
	public Map<String, Collection<FieldOccurrence>> getOccurrencesAsMap() {
		loadOccurrencesIntoMultimap();
		return occurrencessByFieldName.asMap();
	
	}

	public void addFieldOccurrence(FieldOccurrence occr) throws EntityRelationException {
		
		occurrencessByFieldName = null;
		
		if ( occr.getContent() == null )
			throw new EntityRelationException("FieldOccurrenceContainder::addFieldOccurrence Error: Data must not be null");
		
		// using set enforce this, so check is no longer needed
		if ( !occurrences.contains(occr) ) { // add the occurrence only if is new
			occurrencessByFieldName = null;
		//	occr.addContainer(this);
			this.occurrences.add(occr);
			//dirty = true;
		}
	}
	
	public void addFieldOccurrences(Collection<FieldOccurrence> occrs) throws EntityRelationException {
		
		for (FieldOccurrence occr : occrs) 
			this.addFieldOccurrence(occr);
		
	}
	
	public void removeAllFieldOccurrences() {
		this.occurrences = new HashSet<FieldOccurrence>(); // blank occurrences in the duplicate
		this.occurrencessByFieldName = null;
	}

	
	
}
