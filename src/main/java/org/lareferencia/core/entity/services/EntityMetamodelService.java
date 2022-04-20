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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.repositories.jpa.EntityFieldTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.RelationTypeRepository;
import org.lareferencia.core.entity.xml.XMLEntityRelationMetamodel;
import org.lareferencia.core.entity.xml.XMLEntityType;
import org.lareferencia.core.entity.xml.XMLField;
import org.lareferencia.core.entity.xml.XMLRelationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

@Service
public class EntityMetamodelService {

	@Autowired
	EntityTypeRepository entityTypeRepository;

	@Autowired
	EntityFieldTypeRepository entityFieldRepository;

	@Autowired
	RelationTypeRepository relationTypeRepository;

	
	public EntityMetamodelService() {
	}
	
	/**
	 * Procesa recursivamente un XmlEntityField para obtener un EntityFieldType
	 * @param xmlField
	 * @return
	 */
	private FieldType XmlEntityField2EntityField(XMLField xmlField) {
		
		FieldType field = new FieldType( xmlField.getName() );
		field.setDescription(xmlField.getDescription());
		field.setMaxOccurs(xmlField.getMaxOccurs());
			
		if ( xmlField.getFields().size() > 0 )
			field.setKind( FieldType.Kind.COMPLEX );
		
		for ( XMLField xmlSubField : xmlField.getFields() ) {
			//FieldType subfield = XmlEntityField2EntityField(xmlSubField);
			field.addSubfield(xmlSubField.getName());
		}
		
		return field;
	}
	
	/**
	 * Procesa recursivamtne un EntityField para obtenter un XmlEntityField
	 * @param field
	 * @return
	 */
	private XMLField EntityField2XmlEntityField(FieldType field) {
		
		XMLField xmlField = new XMLField(field.getName(), field.getMaxOccurs() );
		xmlField.setDescription( field.getDescription() );
			
		for ( String subfield : field.getSubfields()  ) {
			XMLField xmlSubField = new XMLField(subfield);
			xmlField.addField(xmlSubField);
		}
		
		return xmlField;
	}

	public void persist(XMLEntityRelationMetamodel er) throws Exception {

		Map<String, EntityType> entitiesMap = new HashMap<String, EntityType>();

		for (XMLEntityType xmlEntity : er.getEntities()) {

			EntityType entity = new EntityType( xmlEntity.getName() );
			entity.setDescription( xmlEntity.getDescription() );
			
			for ( XMLField xmlField : xmlEntity.getFields() ) {
				
				FieldType field = XmlEntityField2EntityField(xmlField);
				entity.addField(field);				
			}
			
			entitiesMap.put(entity.getName() , entity);		
		}
		
		for ( Entry<String, EntityType> entry: entitiesMap.entrySet()  ) {
			entityTypeRepository.save(entry.getValue());
		}
	
		for (XMLRelationType xmlRelation : er.getRelations() ) {
			
			if ( xmlRelation.getName() == null )
				throw new EntityRelationException( "Missing relation name!!" );
			
			RelationType relation = new RelationType(xmlRelation.getName());
			relation.setDescription(xmlRelation.getDescription());
			
			if ( xmlRelation.getFromEntityName() != null && xmlRelation.getToEntityName() != null) {
			
				EntityType fromEntityType  = entitiesMap.get( xmlRelation.getFromEntityName() );
				if ( fromEntityType == null ) throw new EntityRelationException(  xmlRelation.getFromEntityName() + ":: Unknown From Entity reference:  " +  xmlRelation.getName()  );
			
				EntityType toEntityType  = entitiesMap.get( xmlRelation.getToEntityName() );
				if ( toEntityType == null ) throw new EntityRelationException(  xmlRelation.getToEntityName() + ":: Unknown To Entity reference:  " +  xmlRelation.getName()  );
			
				
				relation.setFromEntityType(fromEntityType);
				relation.setToEntityType(toEntityType);
			}
			else
				throw new Exception( xmlRelation.getName()  + ":: Missing from or to entity references  "     );
			
			for ( XMLField xmlField : xmlRelation.getFields() ) {
				FieldType field = XmlEntityField2EntityField(xmlField);
				relation.addField(field);				
			}
			
			relationTypeRepository.save(relation);
		}
	}
		
	public XMLEntityRelationMetamodel getConfigFromDB() {
		
		XMLEntityRelationMetamodel config = new XMLEntityRelationMetamodel();
		
		List<EntityType> entites = entityTypeRepository.findAll();
		
		for (EntityType entityType: entites ) {
			
			XMLEntityType xmlEntityType = new XMLEntityType(entityType.getName(), entityType.getDescription());
			
			for ( FieldType field : entityType.getFields()  ) {
				XMLField xmlField = EntityField2XmlEntityField(field);
				xmlEntityType.addField(xmlField);
			}
			
			config.addEntity(xmlEntityType);
		}
		
		return config;
	}
	
	
	public XMLEntityRelationMetamodel loadConfigFromXml(String xml) {

		XMLEntityRelationMetamodel config = new XMLEntityRelationMetamodel();

		try {
			JAXBContext context = JAXBContext.newInstance(config.getClass());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			
			StringReader stringReader = new StringReader( xml.toString() );
			Unmarshaller unmarshaller = context.createUnmarshaller();
			
			config = (XMLEntityRelationMetamodel) unmarshaller.unmarshal(stringReader);
			
			return config;

		} catch (PropertyException e) {
			e.printStackTrace(); //TODO: Exception managment
		} catch (JAXBException e) {
			e.printStackTrace(); //TODO: Exception managment
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return config;

	}
	
	
	public XMLEntityRelationMetamodel loadConfigFromDocument(Document document) {

		XMLEntityRelationMetamodel config = new XMLEntityRelationMetamodel();

		try {
			JAXBContext context = JAXBContext.newInstance(config.getClass());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			
			Unmarshaller unmarshaller = context.createUnmarshaller();
			
			config = (XMLEntityRelationMetamodel) unmarshaller.unmarshal(document);
			
			return config;

		} catch (PropertyException e) {
			e.printStackTrace(); //TODO: Exception managment
		} catch (JAXBException e) {
			e.printStackTrace(); //TODO: Exception managment
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return config;

	}
	
	public String saveConfigToXml(XMLEntityRelationMetamodel config) {

		StringWriter outputWriter = new StringWriter();

		try {
			JAXBContext context = JAXBContext.newInstance(config.getClass());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.marshal(config, outputWriter);

		} catch (PropertyException e) {
			e.printStackTrace(); //TODO: Exception managment
		} catch (JAXBException e) {
			e.printStackTrace(); //TODO: Exception managment
		}

		return outputWriter.toString();

	}

}
