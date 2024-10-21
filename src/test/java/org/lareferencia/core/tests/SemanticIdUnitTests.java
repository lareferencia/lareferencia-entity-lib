
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
import org.lareferencia.core.entity.services.SemanticIdentifierCachedStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SemanticIdUnitTests {

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

    
    @Test
    @Transactional
    public void test_sematict_id_creation()  {

		// Create a new semantic id
		SemanticIdentifier semanticIdentifier = new SemanticIdentifier(SEMANTIC_ID_1);

		// Save it
		semanticIdentifierRepository.saveAndFlush(semanticIdentifier);

		// Check that the semantic id was created
		assertThat( semanticIdentifierRepository.findById(SEMANTIC_ID_1).isPresent() ).isTrue();

    }

	@Test
	@Transactional
	public void test_semantic_id_store() {

		// Create a new semantic id and save it
		SemanticIdentifier s1 = new SemanticIdentifier(SEMANTIC_ID_1);
		semanticIdentifierRepository.saveAndFlush(s1);

		SemanticIdentifierCachedStore  semanticIdentifierCachedStore = new SemanticIdentifierCachedStore(semanticIdentifierRepository, 1000);

		// try to get it from the cache
		Loaded<SemanticIdentifier> s1a = semanticIdentifierCachedStore.loadOrCreate(SEMANTIC_ID_1);
	
		// Check that the semantic id was created
		assertThat( s1a.get() ).isNotNull();
		
		// Asert that already existed and was loaded
		assertThat( s1a.wasCreated() ).isFalse();

		Loaded<SemanticIdentifier> s2 = semanticIdentifierCachedStore.loadOrCreate(SEMANTIC_ID_2);

		// check that the semantic id was created
		assertThat( s2.get() ).isNotNull();

		// Asert that not existed and was created
		assertThat( s2.wasCreated() ).isTrue();

	}

	@Test
	@Transactional
	public void test_sourceentities_and_semanticids() {

		// Create a new provenance
		Provenance provenance = new Provenance("source", "record");
		provenanceRepository.saveAndFlush(provenance);

    	EntityType personEntityType = new EntityType("Person");
		entityTypeRepository.saveAndFlush(personEntityType);

		// Create SourceEntity
		SourceEntity sourceEntity = new SourceEntity(personEntityType, provenance);
		sourceEntityRepository.saveAndFlush(sourceEntity);

		// Check that the sourceEntity was created
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).isPresent()).isTrue();

		// Check that the sourceEntity has the correct values
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).get().getEntityType().getName()).isEqualTo("Person");

		// Check that the sourceEntity has the correct provenance
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).get().getProvenance().getSource()).isEqualTo("source");

		// Check that the sourceEntity has the correct provenance
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).get().getProvenance().getRecord()).isEqualTo("record");

		// Check that the sourceEntity has the correct source and record fields
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).get().getSource()).isEqualTo("source");
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).get().getRecord()).isEqualTo("record");

		// Check that the sourceEntity has the correct provenance
		assertThat(sourceEntityRepository.findById(sourceEntity.getId()).get().getProvenance().getLastUpdate()).isNull();




	}

	

    
 
}