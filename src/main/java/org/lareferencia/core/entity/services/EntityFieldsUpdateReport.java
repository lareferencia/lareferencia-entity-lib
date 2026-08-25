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

package org.lareferencia.core.entity.services;

public class EntityFieldsUpdateReport {

	private int processedEntities;
	private int addedFields;
	private int ignoredFields;

	public int getProcessedEntities() {
		return processedEntities;
	}

	public int getAddedFields() {
		return addedFields;
	}

	public int getIgnoredFields() {
		return ignoredFields;
	}

	public void incrementProcessedEntities() {
		processedEntities++;
	}

	public void incrementAddedFields() {
		addedFields++;
	}

	public void incrementIgnoredFields() {
		ignoredFields++;
	}

	@Override
	public String toString() {
		return String.format(
			"Entidades procesadas: %d%nCampos agregados: %d%nCampos existentes ignorados: %d",
			processedEntities,
			addedFields,
			ignoredFields
		);
	}
}
