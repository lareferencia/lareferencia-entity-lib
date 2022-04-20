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

package org.lareferencia.core.entity.indexing.service;

import java.util.Collection;

import org.lareferencia.core.entity.domain.Entity;

public interface IEntityIndexer {
	
	void setConfig(String configFilePath) throws EntityIndexingException;
	public void index(Entity entity) throws EntityIndexingException;
	public void delete(String entityId) throws EntityIndexingException;
	public void deleteAll(Collection<String> idList) throws EntityIndexingException;
	public void flush() throws EntityIndexingException;
	

}
