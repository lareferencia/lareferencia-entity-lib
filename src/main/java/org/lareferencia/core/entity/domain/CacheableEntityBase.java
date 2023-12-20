
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

import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class CacheableEntityBase<T> implements ICacheableEntity<T> {


	public CacheableEntityBase() {
		_isNew = true;
	}

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
