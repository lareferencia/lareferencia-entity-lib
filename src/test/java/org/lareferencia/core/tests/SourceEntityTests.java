
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

package org.lareferencia.core.tests;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.Loaded;
import org.lareferencia.core.entity.domain.Provenance;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.domain.SourceEntity;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.ProvenanceRepository;
import org.lareferencia.core.entity.repositories.jpa.SemanticIdentifierRepository;
import org.lareferencia.core.entity.repositories.jpa.SourceEntityRepository;
import org.lareferencia.core.entity.services.ProvenanceStore;
import org.lareferencia.core.entity.services.SemanticIdentifierCachedStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SourceEntityTests {

@Autowired
	ProvenanceRepository provenanceRepository;

	@Autowired
	SemanticIdentifierRepository semanticIdentifierRepository;

	@Autowired
	SourceEntityRepository sourceEntityRepository;

	@Autowired
	EntityTypeRepository entityTypeRepository;
 
	public static final String SEMANTIC_ID_1 = "http://www.recolecta.net/semanticidentifier/1234567890";
	public static final String SEMANTIC_ID_2 = "http://www.recolecta.net/semanticidentifier/1234567891";
	public static final String SEMANTIC_ID_3 = "http://www.recolecta.net/semanticidentifier/1234567892";

	public static final String PROVENANCE_ID_1 = "source1";
	public static final String PROVENANCE_RECORD_ID_1 = "record1";
 
    @Transactional
    @Test
    public void test_source_entity_load()  {


		ProvenanceStore provenanceStore = new ProvenanceStore(provenanceRepository);
		SemanticIdentifierCachedStore semanticIdentifierCachedStore = new SemanticIdentifierCachedStore(semanticIdentifierRepository, 10);

		// Create a new semantic id
		Loaded<Provenance> provenance = provenanceStore.loadOrCreate( PROVENANCE_ID_1, PROVENANCE_RECORD_ID_1 );

		// Create a new semantic id
		Loaded<SemanticIdentifier> semanticIdentifier = semanticIdentifierCachedStore.loadOrCreate( SEMANTIC_ID_1, false );

		EntityType personEntityType = new EntityType("Person");
		entityTypeRepository.saveAndFlush(personEntityType);

		// Create SourceEntity and add semantic identifier
		SourceEntity sourceEntity = new SourceEntity(personEntityType, provenance.get());
		sourceEntity.addSemanticIdentifier(semanticIdentifier.get());

		// Save it
		sourceEntityRepository.saveAndFlush(sourceEntity);

		// Check that the semantic id was created 
		assertThat( sourceEntityRepository.findById(sourceEntity.getId()).isPresent() ).isTrue();

		assertThat( sourceEntityRepository.findAll() ).hasSize(1);

		Set<String> semanticIdentifiers = sourceEntity.getSemanticIdentifiers().stream().map( si -> si.getIdentifier() ).collect(Collectors.toSet());


		// Retrieve the source entity by semantics id and check that it is the same
		SourceEntity sourceEntity3 = sourceEntityRepository.findOneBySemanticIdentifiers(semanticIdentifiers);

		assertThat( sourceEntity3 ).isNotNull();

		// Retrieve the source entity by semantic id and check that it is the same
		SourceEntity sourceEntity2 = sourceEntityRepository.findOneByEntityTypeAndProvenanceAndSemanticIdentifiers(personEntityType.getId(), PROVENANCE_ID_1, PROVENANCE_RECORD_ID_1, semanticIdentifiers); 

		assertThat( sourceEntity2 ).isNotNull();

		



   
    }

	

    
 
}