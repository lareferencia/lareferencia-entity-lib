
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

import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.FieldType.Kind;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.RelationTypeRepository;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityMetamodelService;
import org.lareferencia.core.entity.xml.XMLEntityRelationData;
import org.lareferencia.core.entity.xml.XMLEntityRelationMetamodel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EntityRelationUnitTests {
 
 
    @Autowired
    private EntityTypeRepository entityTypeRepository;
    
    @Autowired
    private RelationTypeRepository relationTypeRepository;
    
    @Autowired
    private EntityRepository entityRepository;
    
    @Autowired 
    private EntityMetamodelService modelService;
    
    @Autowired 
    private EntityDataService dataService;
    
    
    @Test
    @Transactional
    public void test_entity_features() throws EntityRelationException {
   
    	/***
    	 *  create and save entity type
    	 */
    	
    	String entityType1Name = "Person"; 
    	    	
    	//given
    	EntityType personEntityType = new EntityType("Person");
    	
    	FieldType nameFieldType = new FieldType("name");
     	FieldType pidFieldType = new FieldType("pid");
        
    	FieldType complexFieldType = new FieldType("complex");
    	complexFieldType.setKind(Kind.COMPLEX);
    	complexFieldType.addSubfield( "subfield1" );
     	complexFieldType.addSubfield( "subfield2" );
        
    	// when
    	personEntityType.setDescription("Description");
    	personEntityType.addField(nameFieldType);
    	personEntityType.addField(pidFieldType);
    	personEntityType.addField(complexFieldType);
        		
    	//then 
    	assertThat( personEntityType.getName() ).isEqualTo( entityType1Name );
    	assertThat( personEntityType.getDescription() ).isEqualTo( "Description" );
    		    	
    	
       	assertThat( personEntityType.getId() ).isNull();
    	entityTypeRepository.save(personEntityType);
       	assertThat( personEntityType.getId() ).isNotNull();
        
       	/********
       	 *  load entity type and create entity instance
       	 */
       	
    	Optional<EntityType> personEntiyType = entityTypeRepository.findOneByName(entityType1Name);
    	assertThat( personEntiyType.get() ).isNotNull();  	
 
    	Entity entity = null;
    	
		if ( personEntiyType.isPresent() )
    		entity = new Entity(personEntiyType.get());
    	
    	assertThat( entity ).isNotNull();  	

    	FieldOccurrence occrName = entity.getEntityType().getFieldByName("name").buildFieldOccurrence().addValue("john");
    	assertThat( occrName ).isNotNull();  	

    	FieldOccurrence occrPID = entity.getEntityType().getFieldByName("pid").buildFieldOccurrence().addValue("1234");
    	assertThat( occrPID ).isNotNull(); 		
    	
    	FieldOccurrence occrComplex = entity.getEntityType()
    			.getFieldByName("complex").buildFieldOccurrence().addValue("subfield1", "s1data")
    			.addValue("subfield2", "s2data");
    	
    	assertThat( occrComplex ).isNotNull(); 		
    	 	
    	assertThat( occrName.getValue() ).isEqualTo("john"); 		
    	assertThat( occrPID.getValue() ).isEqualTo("1234");
    	
    	assertThat( occrComplex.getValue("subfield1") ).isEqualTo("s1data");
    	assertThat( occrComplex.getValue("subfield2") ).isEqualTo("s2data");
    	
    	entity.addFieldOccurrence(occrName);
    	entity.addFieldOccurrence(occrPID);
    	entity.addFieldOccurrence(occrComplex);
    	  	
    	entityRepository.save(entity);
       	assertThat( entity.getId() ).isNotNull();
       	
       	UUID entityID = entity.getId();
        
       	/**
       	 * 
       	 * load_entity_instance_and_test_fieldoccurrence_deduplication
       	 */

   	
    	entity = entityRepository.getOne( entityID );
    	
    	// Load occurrences map to trigger lazy loading
    	entity.getOccurrencesAsMap();
    	
    	// check that there is only one occrs of field name and its john
    	Collection<FieldOccurrence> occrs = entity.getFieldOccurrences("name");
    	assertThat(occrs.size()).isEqualTo(1);
    	assertThat( occrs.stream().findFirst().get().getValue() ).isEqualTo("john");
    	assertThat( occrs.stream().findFirst().get().getLang() ).isNull(); // lang must be null
    	
    	// that there is only one occrs of field pid and its 1234
    	occrs = entity.getFieldOccurrences("pid");
    	assertThat(occrs.size()).isEqualTo(1);
    	assertThat( occrs.stream().findFirst().get().getValue() ).isEqualTo("1234");
    	
    	// that there is only one occrs of field complex
    	occrs = entity.getFieldOccurrences("complex");
    	assertThat(occrs.size()).isEqualTo(1);
    	
    	// get the first occr of complex field
    	occrComplex = occrs.iterator().next();
  
      	assertThat( occrComplex.getValue("subfield1") ).isEqualTo("s1data");
    	assertThat( occrComplex.getValue("subfield2") ).isEqualTo("s2data");
    	
    	// Adding john again should have no effect (because the value is the same)
    	occrName = entity.getEntityType().getFieldByName("name").buildFieldOccurrence().addValue("john");
    	entity.addFieldOccurrence(occrName);
    	entity.getOccurrencesAsMap(); // Reload map after adding occurrence
    	occrs = entity.getFieldOccurrences("name");
    	assertThat( occrs.size() ).isEqualTo(1); // still size 1

    	// Now we add john as different lang occurrence, so must be treated as different
    	occrName = entity.getEntityType().getFieldByName("name").buildFieldOccurrence().addValue("john");
    	occrName.setLang("en");
     	entity.addFieldOccurrence(occrName);
    	entity.getOccurrencesAsMap(); // Reload map after adding occurrence
    	occrs = entity.getFieldOccurrences("name");
    	assertThat( occrs.size() ).isEqualTo(2); // must be 2

    	// Now we add a completely different value, peter 
    	occrName = entity.getEntityType().getFieldByName("name").buildFieldOccurrence().addValue("peter");
    	entity.addFieldOccurrence(occrName);
    	entity.getOccurrencesAsMap(); // Reload map after adding occurrence
    	occrs = entity.getFieldOccurrences("name");
    	assertThat( occrs.size() ).isEqualTo(3); // must be 3

    	// Now we add the same value but to a different field 
    	occrPID = entity.getEntityType().getFieldByName("pid").buildFieldOccurrence().addValue("john");
    	entity.addFieldOccurrence(occrPID);
    	entity.getOccurrencesAsMap(); // Reload map after adding occurrence

    	occrs = entity.getFieldOccurrences("name");
    	assertThat(occrs.size()).isEqualTo(3); // must still be 3
    	
    	occrs = entity.getFieldOccurrences("pid");
    	assertThat(occrs.size()).isEqualTo(2); // must be 2
    	
    	
    	/****
    	 *  load from db and test again
    	 */
    	
    	entity = entityRepository.getOne( entityID );
    	
    	// Load occurrences map to trigger lazy loading
    	entity.getOccurrencesAsMap();
    	
    	occrs = entity.getFieldOccurrences("name");
    	assertThat(occrs.size()).isEqualTo(3); // name occrs size must be 3
    	
    	occrs = entity.getFieldOccurrences("pid");
    	assertThat(occrs.size()).isEqualTo(2); // name occrs size must be 2
    	
      	
    	
    }
    
   
    private Document getXmlDocumentFromResourcePath(String resourcePath) throws Exception {
    	
    	InputStream resource = new ClassPathResource(resourcePath).getInputStream();
    	DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = dBuilder.parse(resource);
		
    	return doc;
    }
    

    @Test
    @Transactional
    public void test_entity_model_parsing() throws Exception {
    
    	Document doc = getXmlDocumentFromResourcePath("entity-model-test1.xml");
    	XMLEntityRelationMetamodel xmlModel = modelService.loadConfigFromDocument(doc);
    	
    	assertThat(xmlModel).isNotNull();
    	
  
    }
    
    
    @Test
    @Transactional
    public void test_entity_data_parsing() throws Exception {
    
    	Document doc = getXmlDocumentFromResourcePath("entity-data-test1.xml");
    	XMLEntityRelationData data = dataService.parseEntityRelationDataFromXmlDocumentNonTransactional(doc);
    	
    	assertThat(data).isNotNull();

    }    
//  @Test
//  @Transactional
//  public void test_entity_model_and_data_xml_loading() throws Exception {
//  
//  	Document doc = getXmlDocumentFromResourcePath("simple_model.xml");
//  	XMLEntityRelationMetamodel xmlModel = modelService.loadConfigFromDocument(doc);
//  	assertThat(xmlModel).isNotNull();
//  	modelService.persist(xmlModel);
//  	
//  	doc = getXmlDocumentFromResourcePath("simple_data_1.xml");
//  	dataService.parseAndPersistEntityRelationDataFromXMLDocument(doc);
//  	
//
//}
//    @Test
//    @Transactional
//    public void test_entity_model_and_data_persist() throws Exception {
//    
//    	Document doc = getXmlDocumentFromResourcePath("entity-model-test1.xml");
//    	XMLEntityRelationMetamodel xmlModel = modelService.loadConfigFromDocument(doc);
//    	assertThat(xmlModel).isNotNull();
//    	modelService.persist(xmlModel);
//    	
//    	doc = getXmlDocumentFromResourcePath("entity-data-test1.xml");
//    	dataService.parseAndPersistEntityRelationDataFromXMLDocument(doc);
//    	
//    	// find by semanticId
//    	Entity entity1 = dataService.findEntityBySemanticId("lattes::4687858846001290").get();
//    	assertThat(entity1).isNotNull();
//    	
//    	Entity entity2 = dataService.findEntityBySemanticId("orcid::0000-0001-5057-9936").get();
//    	assertThat(entity1).isNotNull();
//    
//    	assertThat(entity1).isEqualTo(entity2);
//    	
//    	List<String> semanticIdentifiers = new ArrayList<String>();
//    	semanticIdentifiers.add("lattes::4687858846001290");
//    	semanticIdentifiers.add("orcid::0000-0001-5057-9936");
//    	semanticIdentifiers.add("dummy:1231231231232");
//    	Set<Entity> entities =  dataService.findEntityBySemanticIds(semanticIdentifiers);
//    	assertThat(entities.size()).isEqualTo(1);
//    	assertThat(entities.iterator().next()).isEqualTo(entity1);
//    	
//    	// find by provenace and semanticId
//    	Entity entity3 = entityRepository.findByProvenaceAndSemanticIdentifier("LATTES::4687858846001290", "orcid::0000-0001-5057-9936").get();
//    	assertThat(entity3).isEqualTo(entity1);
//
//    	entities =  entityRepository.findByProvenaceAndAnySemanticIdentifiers("LATTES::4687858846001290", semanticIdentifiers);
//    	assertThat(entities.size()).isEqualTo(1);
//    	assertThat(entities.iterator().next()).isEqualTo(entity1);
//    	
//    	entities =  entityRepository.findByProvenaceAndLastUpdateAndAnySemanticIdentifiers("LATTES::4687858846001290", new Date(), semanticIdentifiers);
//    	assertThat(entities.size()).isEqualTo(1);
//    	assertThat(entities.iterator().next()).isEqualTo(entity1);
//    	
//    	   	
//    	System.out.println("The end");
//    }
//    
   
    
    
 
}