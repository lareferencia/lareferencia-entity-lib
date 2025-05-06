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

import java.util.UUID;

import org.lareferencia.core.entity.domain.RelationId;
import org.lareferencia.core.entity.domain.SourceRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;


//@RepositoryRestResource(path = "relation", collectionResourceRel = "relation")
@RepositoryRestResource(exported = false)
public interface SourceRelationRepository extends JpaRepository<SourceRelation, RelationId> {
	
	
//	Optional<Relation> findByRelationTypeAndToEntityAndFromEntity(RelationType rtype, Entity fromEntity, Entity toEntity);	
//	
//	@Query(value="SELECT relation.* FROM relation WHERE relation.relation_type_id = ?1 and relation.from_entity_id = ?2 and relation.to_entity_id = ?3 limit 1", nativeQuery = true)
//	Optional<Relation> findRelationByTypeAndMembers(Long relationTypeId, UUID fromEntityId, UUID toEntityId); 
//	
//	@Query(value="SELECT relation.* FROM relation WHERE relation.from_entity_id = ?1 or relation.to_entity_id = ?1", nativeQuery = true)
//	List<Relation> findRelationsByEntityId(UUID entityId);
//	
//	@Query(value="SELECT relation.* FROM relation WHERE relation.relation_type_id = ?1 and (relation.from_entity_id = ?2 or relation.to_entity_id = ?2)", nativeQuery = true)
//	List<Relation> findRelationsByTypeAndEntityId(Long relationTypeId, UUID entityId);
//	
//	@Query(value="SELECT relation.* FROM relation WHERE relation.relation_type_id in (?1) and (relation.from_entity_id = ?2 or relation.to_entity_id = ?2)", nativeQuery = true)
//	List<Relation> findRelationsByTypesAndEntityId(Collection<Long> relationTypeIdList, UUID entityId);
//	
//	
//	@Query(value="select r.* from relation r \n" + 
//			"where ( r.to_entity_id = ?1 and not exists (select t.uuid from relation t where t.to_entity_id = ?2 and t.from_entity_id = r.from_entity_id) ) \n" + 
//			"or    ( r.from_entity_id = ?1 and not exists (select s.uuid from relation s where s.from_entity_id = ?2 and s.to_entity_id = r.to_entity_id) ) ", nativeQuery = true )
//	List<Relation> searchUpdatableRelations(UUID oldId, UUID newId);
//	
//	@Modifying
//	@Query(value="delete from relation r where r.from_entity_id = ?1 or r.to_entity_id = ?1 ", nativeQuery = true )
//	void deleteRelationWithMember(UUID memberId);

}
