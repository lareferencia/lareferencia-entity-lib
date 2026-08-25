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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.RelationTypeRepository;
import org.lareferencia.core.entity.services.EntityFieldsUpdateReport;
import org.lareferencia.core.entity.services.EntityMetamodelService;
import org.lareferencia.core.entity.services.EntityModelCache;
import org.lareferencia.core.entity.xml.XMLEntityRelationMetamodel;
import org.lareferencia.core.entity.xml.XMLEntityType;
import org.lareferencia.core.entity.xml.XMLField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.liquibase.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Entity metamodel additive field update tests")
class EntityMetamodelUpdateIntegrationTest {

	@Autowired
	private EntityMetamodelService modelService;

	@Autowired
	private EntityModelCache entityModelCache;

	@Autowired
	private EntityTypeRepository entityTypeRepository;

	@Autowired
	private RelationTypeRepository relationTypeRepository;

	private String personTypeName;
	private String organizationTypeName;
	private String relationTypeName;

	@BeforeEach
	void setUp() {
		String testSuffix = UUID.randomUUID().toString().replace("-", "");
		personTypeName = "Person" + testSuffix;
		organizationTypeName = "Organization" + testSuffix;
		relationTypeName = "Authorship" + testSuffix;
		entityModelCache.invalidate();
		createBaseMetamodel();
		entityModelCache.invalidate();
	}

	@Test
	@DisplayName("Should add only missing fields to existing entity types")
	void shouldAddOnlyMissingFields() throws Exception {
		XMLEntityRelationMetamodel update = new XMLEntityRelationMetamodel();
		XMLEntityType person = new XMLEntityType(personTypeName);
		person.addField(new XMLField("name"));
		person.addField(new XMLField("identifier.scopus"));
		person.addField(complexField("profile", "summary", "homepage"));
		update.addEntity(person);

		EntityFieldsUpdateReport report = modelService.updateEntityFields(update);

		EntityType reloadedPerson = entityTypeRepository.findOneByName(personTypeName).get();
		assertThat(report.getProcessedEntities()).isEqualTo(1);
		assertThat(report.getAddedFields()).isEqualTo(2);
		assertThat(report.getIgnoredFields()).isEqualTo(1);
		assertThat(reloadedPerson.getFieldNames()).contains("name", "identifier.orcid", "identifier.scopus", "profile");
		assertThat(reloadedPerson.getFieldByName("profile").getSubfields()).containsExactly("summary", "homepage");
	}

	@Test
	@DisplayName("Should be idempotent when the same update is executed twice")
	void shouldBeIdempotent() throws Exception {
		XMLEntityRelationMetamodel update = new XMLEntityRelationMetamodel();
		XMLEntityType person = new XMLEntityType(personTypeName);
		person.addField(new XMLField("identifier.scopus"));
		update.addEntity(person);

		EntityFieldsUpdateReport firstReport = modelService.updateEntityFields(update);
		EntityFieldsUpdateReport secondReport = modelService.updateEntityFields(update);

		EntityType reloadedPerson = entityTypeRepository.findOneByName(personTypeName).get();
		assertThat(firstReport.getAddedFields()).isEqualTo(1);
		assertThat(firstReport.getIgnoredFields()).isZero();
		assertThat(secondReport.getAddedFields()).isZero();
		assertThat(secondReport.getIgnoredFields()).isEqualTo(1);
		assertThat(reloadedPerson.getFields())
			.extracting(FieldType::getName)
			.filteredOn("identifier.scopus"::equals)
			.hasSize(1);
	}

	@Test
	@DisplayName("Should treat field identity as entity name plus field name")
	void shouldAddSameFieldNameToDifferentEntities() throws Exception {
		XMLEntityRelationMetamodel update = new XMLEntityRelationMetamodel();
		XMLEntityType person = new XMLEntityType(personTypeName);
		person.addField(new XMLField("identifier.external"));
		XMLEntityType organization = new XMLEntityType(organizationTypeName);
		organization.addField(new XMLField("identifier.external"));
		update.addEntity(person);
		update.addEntity(organization);

		EntityFieldsUpdateReport report = modelService.updateEntityFields(update);

		assertThat(report.getProcessedEntities()).isEqualTo(2);
		assertThat(report.getAddedFields()).isEqualTo(2);
		assertThat(entityTypeRepository.findOneByName(personTypeName).get().getFieldNames()).contains("identifier.external");
		assertThat(entityTypeRepository.findOneByName(organizationTypeName).get().getFieldNames()).contains("identifier.external");
	}

	@Test
	@DisplayName("Should roll back all changes when an entity does not exist")
	void shouldRollbackWhenEntityDoesNotExist() throws Exception {
		XMLEntityRelationMetamodel update = new XMLEntityRelationMetamodel();
		XMLEntityType person = new XMLEntityType(personTypeName);
		person.addField(new XMLField("identifier.scopus"));
		XMLEntityType unknown = new XMLEntityType("Project");
		unknown.addField(new XMLField("name"));
		update.addEntity(person);
		update.addEntity(unknown);

		assertThatThrownBy(() -> modelService.updateEntityFields(update))
			.isInstanceOf(EntityRelationException.class)
			.hasMessage("Cannot update entity fields: EntityType 'Project' does not exist");

		assertThat(entityTypeRepository.findOneByName(personTypeName).get().getFieldNames()).doesNotContain("identifier.scopus");
	}

	@Test
	@DisplayName("Should leave relations and existing field definitions unchanged")
	void shouldNotModifyRelationsOrExistingFields() throws Exception {
		XMLEntityRelationMetamodel update = new XMLEntityRelationMetamodel();
		XMLEntityType person = new XMLEntityType(personTypeName);
		XMLField existingName = new XMLField("name", 7);
		existingName.setDescription("Updated description that must be ignored");
		existingName.addField(new XMLField("ignored-subfield"));
		person.addField(existingName);
		person.addField(new XMLField("identifier.scopus"));
		update.addEntity(person);

		modelService.updateEntityFields(update);

		EntityType reloadedPerson = entityTypeRepository.findOneByName(personTypeName).get();
		FieldType name = reloadedPerson.getFieldByName("name");
		RelationType authorship = relationTypeRepository.findOneByName(relationTypeName).get();

		assertThat(name.getDescription()).isEqualTo("Original name");
		assertThat(name.getMaxOccurs()).isEqualTo(1);
		assertThat(name.getKind()).isEqualTo(FieldType.Kind.SIMPLE);
		assertThat(name.getSubfields()).isEmpty();
		assertThat(authorship.getFieldNames()).containsExactly("order");
	}

	@Test
	@DisplayName("Should expose added fields through EntityModelCache after commit")
	void shouldInvalidateCacheAfterSuccessfulUpdate() throws Exception {
		EntityType cachedPersonBeforeUpdate = entityModelCache.getObjectByName(EntityType.class, personTypeName);

		assertThatThrownBy(() -> cachedPersonBeforeUpdate.getFieldByName("identifier.scopus"))
			.isInstanceOf(EntityRelationException.class);

		XMLEntityRelationMetamodel update = new XMLEntityRelationMetamodel();
		XMLEntityType person = new XMLEntityType(personTypeName);
		person.addField(new XMLField("identifier.scopus"));
		update.addEntity(person);

		modelService.updateEntityFields(update);

		EntityType cachedPerson = entityModelCache.getObjectByName(EntityType.class, personTypeName);
		assertThat(cachedPerson.getFieldByName("identifier.scopus")).isNotNull();
	}

	private void createBaseMetamodel() {
		EntityType person = new EntityType(personTypeName);
		FieldType name = new FieldType("name");
		name.setDescription("Original name");
		person.addField(name);
		person.addField(new FieldType("identifier.orcid"));
		person = entityTypeRepository.save(person);

		EntityType organization = new EntityType(organizationTypeName);
		organization.addField(new FieldType("name"));
		organization = entityTypeRepository.save(organization);

		RelationType authorship = new RelationType(relationTypeName);
		authorship.setFromEntityType(person);
		authorship.setToEntityType(organization);
		authorship.addField(new FieldType("order"));
		relationTypeRepository.save(authorship);
	}

	private XMLField complexField(String name, String... subfieldNames) {
		XMLField field = new XMLField(name);
		for (String subfieldName : subfieldNames) {
			field.addField(new XMLField(subfieldName));
		}
		return field;
	}
}
