
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
import org.lareferencia.core.entity.domain.Provenance;
import org.lareferencia.core.entity.domain.ProvenanceId;
import org.lareferencia.core.entity.domain.SourceEntity;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.ProvenanceRepository;
import org.lareferencia.core.entity.repositories.jpa.SourceEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ProvenanceUnitTests {

	@Autowired
	ProvenanceRepository provenanceRepository;

	@Autowired
	SourceEntityRepository sourceEntityRepository;

	@Autowired
	EntityTypeRepository entityTypeRepository;
 
    
    @Test
    @Transactional
    public void test_provenance()  {
   
		// Create a new provenance
		Provenance provenance = new Provenance("source", "record");
		provenanceRepository.saveAndFlush(provenance);
		
		ProvenanceId id = new ProvenanceId("source", "record");

		// // Check that the provenance was created
		assertThat(provenanceRepository.findById(id ).isPresent()).isTrue();
		
		// // Check that the provenance has the correct values
		assertThat(provenanceRepository.findById(provenance.getId()).get().getSource()).isEqualTo("source");
		assertThat(provenanceRepository.findById(provenance.getId()).get().getRecord()).isEqualTo("record");
		
		// // Update the provenance
		provenance.setLastUpdate( java.time.LocalDateTime.now());
		provenanceRepository.saveAndFlush(provenance);
		
		// // Check that the provenance was updated
		assertThat(provenanceRepository.findById(provenance.getId()).get().getLastUpdate()).isNotNull();
		
		// // Delete the provenance
		provenanceRepository.deleteById(provenance.getId());
		
		// // Check that the provenance was deleted
		assertThat(provenanceRepository.findById(provenance.getId()).isPresent()).isFalse();
    	
    }

	@Test
    @Transactional
    public void test_provenance_and_source_entity()  {

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