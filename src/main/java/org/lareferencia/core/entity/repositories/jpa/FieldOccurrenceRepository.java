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

package org.lareferencia.core.entity.repositories.jpa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.FieldType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;



//@RepositoryRestResource(path = "field_occurrence", collectionResourceRel = "field_occurrence")
@RepositoryRestResource(exported = false)
public interface FieldOccurrenceRepository extends JpaRepository<FieldOccurrence, Long> {

	//List<FieldOccurrence> findByFieldTypeIdAndContainerId(Long fieldTypeId, UUID containerId);
	//List<FieldOccurrence> findByFieldTypeAndContainerId(FieldType fieldType, UUID containerId);
	
	 //List<FieldOccurrence> findByContainerId(UUID containerId);

	@Query(value = "select fo.* from field_occurrence fo, entity_fieldoccr ef, field_type ft where fo.id = ef.fieldoccr_id and ef.entity_id = ?1 and fo.field_type_id = ft.id and ft.name = ?2 ", nativeQuery = true)
	Collection<FieldOccurrence> getEntityFieldOccurrencesByEntityIdAndFieldName(UUID entityId, String fieldName);

	@Query(value = "select fo.* from field_occurrence fo, relation_fieldoccr rf, field_type ft where fo.id = rf.fieldoccr_id and rf.relation_id = ?1 and fo.field_type_id = ft.id and ft.name = ?2 ", nativeQuery = true)	
	Collection<FieldOccurrence> getRelationFieldOccurrencesByRelationIdAndFieldName(UUID relationId, String fieldName);

	
}
