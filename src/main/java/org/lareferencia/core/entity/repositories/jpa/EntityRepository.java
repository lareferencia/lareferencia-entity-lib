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


import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;


@RepositoryRestResource(path = Entity.NAME, collectionResourceRel = Entity.NAME)
public interface EntityRepository extends JpaRepository<Entity, UUID> {
	
	
	Page<Entity> findByEntityType(EntityType type, Pageable pageable);

	Page<Entity> findBySemanticIdentifiers_Identifier(String semanticIdentifier, Pageable pageable);
	
	@Query(value="SELECT entity.* FROM entity, semantic_identifier s WHERE s.entity_id = entity.uuid and s.semantic_id = ?1", nativeQuery=true)
	List<Entity> findBySemanticIdentifierStrId(String semanticIdentifier);
	
	@Query(value="SELECT entity.* FROM entity, semantic_identifier s WHERE entity.duplicate_type = 0 and s.entity_id = entity.uuid and s.semantic_id = ?1", nativeQuery=true)
	Set<Entity> findNonDuplicateEntitiesBySemanticIdentifierStrId(String semanticIdentifier);
	
	
	@Query(value="SELECT entity.* FROM entity, semantic_identifier s WHERE s.entity_id = entity.uuid and s.semantic_id in (?1)", nativeQuery=true)
	Set<Entity> findByAnySemanticIdentifierStrId(List<String> semanticIdentifiers);
	
	@Query(value="SELECT entity.* FROM entity WHERE entity.uuid IN (?1)", nativeQuery=true)
	Set<Entity> findByEntityIds(List<UUID> entityIds);
	
	@Query(value="SELECT e.* " + 
			"FROM provenance p, source_entity se, entity e " + 
			"WHERE p.source_id = ?1 AND p.record_id = ?2 " + 
			"AND se.deleted = FALSE AND se.provenance_id = p.id AND se.final_entity_id = e.uuid AND e.dirty = FALSE;", nativeQuery = true)
	List<Entity> findByProvenaceSourceAndRecordId(String sourceId, String recordId);
	
	@Query(value="SELECT s.finalEntity " + 
			"FROM SourceEntity s " + 
			"WHERE s.provenance.source = ?1 AND s.deleted = FALSE AND s.finalEntity.dirty = FALSE")  
	Page<Entity> findEntitiesByProvenaceSource(String sourceId, Pageable pageable);
	
	@Query(value="SELECT s.finalEntity " + 
			"FROM SourceEntity s " + 
			"WHERE s.provenance.source = ?1 AND s.deleted = FALSE AND s.finalEntity.dirty = FALSE AND s.finalEntity.entityTypeId = ?2")  
	Page<Entity> findEntitiesByEntityTypeIdAndProvenaceSource(String sourceId, Long entityTypeId, Pageable pageable);
		
	
	@Query("Select r.fromEntity from Relation r where r.id.toEntityId = ?1 and r.id.relationTypeId = ?2")
	Page<Entity> findRelatedFromEntitesByRelationTypeId(UUID entityId, Long relationTypeId, Pageable pageable);
	
	@Query("Select r.toEntity from Relation r where r.id.fromEntityId = ?1 and r.id.relationTypeId = ?2")
	Page<Entity> findRelatedToEntitesByRelationTypeId(UUID entityId, Long relationTypeId, Pageable pageable);
	
	@Query("Select r.fromEntity from Relation r where r.id.toEntityId = ?1 and r.relationType.name = ?2")
	Page<Entity> findRelatedFromEntitesByRelationTypeName(UUID entityId, String relationTypeName, Pageable pageable);
	
	@Query("Select r.toEntity from Relation r where r.id.fromEntityId = ?1 and r.relationType.name = ?2")
	Page<Entity> findRelatedToEntitesByRelationTypeName(UUID entityId, String relationTypeName, Pageable pageable);
//	
	
	/*** Hidden in rest **/

	@RestResource(exported = false)
	@Query(value="(select to_entity_id from relation\n" + 
			"where from_entity_id = '?1' and relation_type_id = ?2)\n" + 
			"union\n" + 
			"(select from_entity_id from relation\n" + 
			"where to_entity_id = '?1' and relation_type_id = ?2)", nativeQuery=true)
	Set<UUID> findRelatedEntitiesIdsByEntityIdAndRelationID(UUID entityId, Long relationId);
	
	@RestResource(exported = false)
	@Query(value="SELECT entity.entity_type_id FROM entity WHERE entity.uuid = ?1", nativeQuery=true)
	Long getEntityTypeIdByEntityId(UUID entityId);
	
	@RestResource(exported = false)
	@Query(value="SELECT e.* FROM entity e,  entity_semantic_identifier esi WHERE e.uuid = esi.entity_id AND esi.semantic_id IN (?1) limit 1", nativeQuery=true)
	Entity findEntityWithSemanticIdentifiers(List<Long> semanticIds);
	
	
	
	/////////////////////////////////// Entity and Relation building from SourceEntity And Relation Entity
	
	@Query(value = "CALL merge_entity_relation_data(0);", nativeQuery = true)
	int mergeEntiyRelationData();
   

	
	
}