
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


import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.lareferencia.core.entity.domain.*;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EntityJSONTests {

    @Test
    @Transactional
    public void test_field_ocurrence_serialization() throws EntityRelationException {

        System.out.println("\n\n");

        //given
        EntityType personEntityType = new EntityType("Person");

        FieldType nameFieldType = new FieldType("name");
        FieldType pidFieldType = new FieldType("pid");

        FieldType complexFieldType = new FieldType("complex");
        complexFieldType.setKind(FieldType.Kind.COMPLEX);
        complexFieldType.addSubfield( "subfield1" );
        complexFieldType.addSubfield( "subfield2" );

        // when
        personEntityType.setDescription("Description");
        personEntityType.addField(nameFieldType);
        personEntityType.addField(pidFieldType);
        personEntityType.addField(complexFieldType);

        SourceEntity entity = new SourceEntity(personEntityType, new Provenance("source", "record"));

        entity.addFieldOccurrence("name", FieldOccurrence.createSimpleFieldOccurrence("john") );
        entity.addFieldOccurrence("name", FieldOccurrence.createSimpleFieldOccurrence("john").setLang("es").setPreferred(true) );
        entity.addFieldOccurrence("pid", FieldOccurrence.createSimpleFieldOccurrence("1234") );
        entity.addFieldOccurrence("pid", FieldOccurrence.createSimpleFieldOccurrence("234") );
        entity.addFieldOccurrence("pid", FieldOccurrence.createSimpleFieldOccurrence("1234").setLang("es") );
        entity.addFieldOccurrence("pid", FieldOccurrence.createSimpleFieldOccurrence("2345") );
        entity.addFieldOccurrence("complex", FieldOccurrence.createComplexFieldOccurrence().addValue("subfield1", "s1data").addValue("subfield2", "s2data") );
 
        MultiMapFieldOcurrenceAttributeConverter converter = new MultiMapFieldOcurrenceAttributeConverter();

        String json = converter.convertToDatabaseColumn(entity.getOccurrencesByFieldName());
        System.out.println(json);

        Multimap<String, FieldOccurrence> mymap = converter.convertToEntityAttribute(json);
        
        for ( String key : mymap.keySet() ) {
        	System.out.println("-->>key: " + key);
        	for ( FieldOccurrence fo : mymap.get(key) ) {

                if ( fo instanceof SimpleFieldOccurrence ) {
                    System.out.println("SimpleFieldOccurrence");
                    System.out.println(fo);
                    System.out.println("value: " + fo.getValue());

                } else if ( fo instanceof ComplexFieldOccurrence ) {
                    System.out.println("ComplexFieldOccurrence");
                    System.out.println(fo);
                    System.out.println("value: " + fo.getContent());
                }
        	}
        }

        System.out.println("\n\n");
    }

    @Test
    @Transactional
    public void test_relation_serialization() throws EntityRelationException {

        System.out.println("\n\n");

        Multimap<String, Relation> relationsByName = LinkedHashMultimap.create();

        Relation r1a = new Relation( UUID.randomUUID() );
        Relation r1b = new Relation( UUID.randomUUID() );
        Relation r2a = new Relation( UUID.randomUUID() );

        r1a.addFieldOccurrence("affiliation", FieldOccurrence.createSimpleFieldOccurrence("affiliation1") );
        r1a.addFieldOccurrence("affiliation", FieldOccurrence.createSimpleFieldOccurrence("affiliation2") );
        r1a.addFieldOccurrence("role", FieldOccurrence.createSimpleFieldOccurrence("role1") );

        r1b.addFieldOccurrence("affiliation", FieldOccurrence.createSimpleFieldOccurrence("affiliation1") );

        relationsByName.put("relation1", r1a);
        relationsByName.put("relation1", r1b);
        relationsByName.put("relation2", r2a);

        System.out.println("relationsByName: " + relationsByName.asMap());

        MultiMapRelationAttributeConverter converter = new MultiMapRelationAttributeConverter();

        String json = converter.convertToDatabaseColumn(relationsByName);
        System.out.println(json);

        Multimap<String, Relation> mymap = converter.convertToEntityAttribute(json);
        System.out.println("relationsByName: " + mymap.asMap());

        assertEquals(relationsByName.toString(), mymap.toString());

        System.out.println("\n\n");
    }

 
}