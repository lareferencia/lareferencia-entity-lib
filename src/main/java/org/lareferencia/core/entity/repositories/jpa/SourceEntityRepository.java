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


import java.util.Set;
import java.util.UUID;

import org.lareferencia.core.entity.domain.SourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;


//@RepositoryRestResource(path = , collectionResourceRel = Entity.NAME)
@RepositoryRestResource(exported = false)
public interface SourceEntityRepository extends JpaRepository<SourceEntity, UUID> {

	@RestResource(exported = false)
	@Modifying
	@Query("update SourceEntity e set e.deleted = true where e.provenanceId = ?1")
	void logicalDeleteByProvenanceId(Long provenanceId);

	/**
	 * Update sourceEntity and other sourceEntities sharing any semantic identifiers to point to the same final Entity
	 * @param sourceEntity
	 * @param finalEntity
	 */
	@RestResource(exported = false)
	@Modifying
	@Query(value="UPDATE source_entity " + 
			"SET final_entity_id = ?2 WHERE uuid IN " + 
			"(SELECT e.entity_id FROM source_entity_semantic_identifier s, source_entity_semantic_identifier e " + 
			" WHERE s.entity_id = ?1 AND s.semantic_id = e.semantic_id )", nativeQuery=true)
	void updateFinalEntityReference(UUID sourceEntity, UUID finalEntity);

	
	
	Set<SourceEntity> findByProvenanceId(Long id);	
	
	@Query(value="SELECT se.* FROM source_entity se, source_entity_semantic_identifier s, source_entity_semantic_identifier e" + 
				 "WHERE s.entity_id = ?1 AND s.semantic_id = e.semantic_id AND e.entity_id != ?1 AND se.uuid = e.entity_id;", nativeQuery=true)
	Set<SourceEntity> findSourceEntitiesWithSharedSemanticIdentifiers(UUID sourceEntityId);

		
	/**
	 * copy semantic identifiers from source entity to final entity ( only if not alreay are there )
	 * @param sourceEntity
	 * @param finalEntity
	 */
	@RestResource(exported = false)
	@Modifying
	@Query(value="	INSERT INTO entity_semantic_identifier\n" + 
			"	SELECT ?2, sesi.semantic_id\n" + 
			"	FROM source_entity_semantic_identifier sesi\n" + 
			"	WHERE sesi.entity_id = ?1 AND sesi.semantic_id NOT IN \n" + 
			"	(SELECT esi.semantic_id FROM entity_semantic_identifier esi WHERE esi.entity_id = ?2)\n", nativeQuery=true)
	void copySemanticIdentifiersFromSourceEntityToEntity(UUID sourceEntity, UUID finalEntity);

	
	
	
}
