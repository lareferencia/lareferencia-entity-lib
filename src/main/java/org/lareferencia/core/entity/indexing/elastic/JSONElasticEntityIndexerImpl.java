
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

package org.lareferencia.core.entity.indexing.elastic;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.lareferencia.core.entity.domain.Entity;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.Relation;
import org.lareferencia.core.entity.domain.RelationType;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.indexing.filters.FieldOccurrenceFilterService;
import org.lareferencia.core.entity.indexing.nested.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;
import org.lareferencia.core.entity.indexing.nested.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.service.EntityIndexingException;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.services.EntityDataService;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.base.BooleanResponse;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.springframework.context.ApplicationContext;

public class JSONElasticEntityIndexerImpl implements IEntityIndexer {

	private static Logger logger = LogManager.getLogger(JSONElasticEntityIndexerImpl.class);

	private IndexingConfiguration indexingConfiguration;
	private String indexingConfigFilename;

	//List<JSONEntityElastic> entityBuffer = new LinkedList<JSONEntityElastic>();

	Map<String, EntityIndexingConfig> configsByEntityType;

	@Autowired
	EntityDataService entityDataService;

	ObjectMapper jsonMapper;

	RestHighLevelClient client = null;

	@Value("${elastic.host:localhost}")
	private String host;

	@Value("${elastic.port:9200}")
	private Integer port;

	@Value("${elastic.username:nouser}")
	private String username;

	@Value("${elastic.password:nopassword}")
	private String password;

	@Value("${elastic.useSSL:false}")
	private Boolean useSSL;

	@Value("${elastic.authenticate:false}")
	private Boolean authenticate;

	BulkRequest bulkRequest;

	private static int MAX_RETRIES = 10;

	@Autowired
	ApplicationContext context;

	// this will be used to filter out fields that are not to be indexed
	// will be injected by spring context on set config method
	FieldOccurrenceFilterService fieldOccurrenceFilterService;

	@Override
	public void index(Entity entity) throws EntityIndexingException {

		try {

			EntityType type = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());

			EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(type.getName());

			if (entityIndexingConfig == null)
				throw new EntityIndexingException(
						"Error indexing entity: " + entity.getId() + " " + this.indexingConfigFilename
								+ " doesn´t contains a indexing config for " + type.getName() + " EntityType");

			Multimap<String, Relation> relationsMap = this.getRelationMultimap(entity);

			JSONEntityElastic elasticEntity = createElasticEntity(entityIndexingConfig, entity, relationsMap);

			for (EntityIndexingConfig nestedEntityConfig : entityIndexingConfig.getIndexNestedEntities()) {

				String relationName = nestedEntityConfig.getEntityRelation();

				for (Relation relation : relationsMap.get(relationName)) {
					Entity relatedEntity = relation.getRelatedEntity(entity.getId());

					Multimap<String, Relation> relatedEntityRelationsMap = this.getRelationMultimap(relatedEntity);

					JSONEntityElastic relatedElasticEntity = createElasticEntity(nestedEntityConfig, relatedEntity,
							relatedEntityRelationsMap);
					elasticEntity.addRelatedEntity(nestedEntityConfig.getName(), relatedElasticEntity);
				}
			}

			indexEntity(elasticEntity, entityIndexingConfig.getName());

		} catch (Exception e) {
			throw new EntityIndexingException("Indexing error. " + e.getMessage());
		}

	}

	public JSONEntityElastic createElasticEntity(EntityIndexingConfig config, Entity entity,
			Multimap<String, Relation> relationsMap) throws EntityIndexingException {

		JSONEntityElastic result = new JSONEntityElastic();

		try {

			result.setId(entity.getId().toString());

			if ( config.getindexEntityType() )
				result.setType(entityDataService.getEntityTypeFromId(entity.getEntityTypeId()).getName());

			if ( config.getIndexSemanticIds() ) {
				for (SemanticIdentifier semanticId : entity.getSemanticIdentifiers())
					result.addSemanticId(semanticId.getIdentifier());
			}

			for (FieldIndexingConfig fieldConfig : config.getIndexFields())
				processFieldConfig(entity, relationsMap, fieldConfig, result);

			// saveIEntity(result);

		} catch (Exception e) {
			throw new EntityIndexingException("Error creating JSONElasticEntity: " + config.getName() + "::" + config.getEntityType());
		}
		return result;
	}

	@Override
	public void flush() throws EntityIndexingException {

		Boolean retry = true;
		int retries = 0;
		int millis = 2500;

		while ( retry ) {

			try {
				BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
				logger.info( "Bulk request result: " + bulkResponse.status().toString() );

				retry = false;
				
				if ( bulkResponse.hasFailures() ) {
				    logger.info( "Bulk request has failures: " + bulkResponse.buildFailureMessage() );
				}

			} catch (Exception e) {
				logger.warn("retrying: " + retries + " -- Warning: " + e.getClass().toString() + " " + e.getMessage() );
				try { Thread.sleep(millis); } catch (Exception se) {};

				retries++;
				millis *= 2; //increment watting time

				if ( retries > MAX_RETRIES )
					throw new EntityIndexingException("Bulk request to elastic failed :: " + e.getClass().toString()  +  e.getMessage());
			}
		}

		//create a new empty request
		bulkRequest = new BulkRequest();
		bulkRequest.timeout("2m");
	}

	@Override
	public void setConfig(String configFilePath) throws EntityIndexingException {

		// load field occurrence filter service and load filters into it
		try {
			fieldOccurrenceFilterService = FieldOccurrenceFilterService.getServiceInstance( context );
			if ( fieldOccurrenceFilterService != null )
				fieldOccurrenceFilterService.loadFiltersFromApplicationContext(context);

			logger.debug( "fieldOccurrenceFilterService: " + fieldOccurrenceFilterService.getFilters().toString() );
		} catch (Exception e) {
			logger.warn("Error loading field occurrence filters: " + e.getMessage());
		}


		try {
			RestClientBuilder builder = RestClient.builder( new HttpHost(host.trim(), port ) );
			// Create the transport with a Jackson mapper
			
			client = new RestHighLevelClient(builder);

		} catch (Exception e) {
			throw new EntityIndexingException("Error connecting elasticsearch/opensearch:" + host + ":" + port + e.getMessage());
		}
	/*
		final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));


		RestClientBuilder builder = RestClient.builder( new HttpHost(host,port,"https") )
				.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
					@Override
					public HttpAsyncClientBuilder customizeHttpClient(
							HttpAsyncClientBuilder httpClientBuilder) {
						return httpClientBuilder
								.setDefaultCredentialsProvider(credentialsProvider);
					}
				});


		try {
			elasticClient = new RestHighLevelClient( builder );
			RestClient client = elasticClient.getLowLevelClient();


			System.out.println ( client.isRunning() );
		} catch (Exception e) {
			throw new EntityIndexingException("Error connecting elasticsearch:" + host+ ":" + port   + e.getMessage());
		}

	*/


		this.indexingConfigFilename = configFilePath ;
		

		try {
			indexingConfiguration = IndexingConfiguration.loadFromXml(configFilePath);

			configsByEntityType = new HashMap<String, EntityIndexingConfig>();

			for (EntityIndexingConfig entityIndexingConfig : indexingConfiguration.getEntityIndices()) {
				// put entity indexing config in the map
				configsByEntityType.put(entityIndexingConfig.getEntityType(), entityIndexingConfig);
				// create/setup mapping
				createOrUpdateIndexMapping(entityIndexingConfig);
			}

			logger.info("Nested Indexer Config File: " + indexingConfigFilename + " loaded");

		} catch (Exception e) {
			throw new EntityIndexingException("Nested Indexer Config File: " + indexingConfigFilename + e.getMessage());
		}

		// Create indices and mappings





		// JSON MAPPER
		jsonMapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(JSONEntityElastic.class, new JSONEntityElasticSerializer());
		jsonMapper.registerModule(module);

		// String serialized = mapper.writeValueAsString(myItem);
		bulkRequest = new BulkRequest();
		bulkRequest.timeout("2m");
	}

	private void createOrUpdateIndexMapping( EntityIndexingConfig entityIndexingConfig ) throws EntityIndexingException {

		// check if index exists
		try {
			Boolean indexExists = client.indices().exists(new GetIndexRequest(entityIndexingConfig.getName() ), RequestOptions.DEFAULT);

			if ( indexExists ) {
				logger.info("Index " + entityIndexingConfig.getName() + " already exists");

			} else {
				logger.info("Index " + entityIndexingConfig.getName() + " does not exist, creating it");
				// create index
				CreateIndexRequest createIndexRequest = new CreateIndexRequest(entityIndexingConfig.getName());
				//createIndexRequest.settings(Settings.builder() //Specify in the settings how many shards you want in the index.
				//		.put("index.number_of_shards", 4)
				//		.put("index.number_of_replicas", 3)
				//);


				//Create a set of maps for the index's mappings.
				HashMap<String, String> typeMapping = new HashMap<String,String>();


				typeMapping.put("type", "text");
				typeMapping.put("index", "true");
				typeMapping.put("analyzer", "standard");
				typeMapping.put("search_analyzer", "standard");
				typeMapping.put("term_vector", "with_positions_offsets");
				typeMapping.put("store", "true");
				typeMapping.put("fielddata", "true");
				typeMapping.put("doc_values", "true");
				typeMapping.put("norms", "true");
				typeMapping.put("boost", "1.0");
				typeMapping.put("null_value", "null");
				typeMapping.put("copy_to", "null");
				typeMapping.put("ignore_above", "2147483647");
				typeMapping.put("similarity", "BM25");
				typeMapping.put("eager_global_ordinals", "false");
				typeMapping.put("index_options", "positions");
				typeMapping.put("store", "true");
				typeMapping.put("fielddata", "true");
				typeMapping.put("doc_values", "true");
				typeMapping.put("norms", "true");
				typeMapping.put("boost", "1.0");
				typeMapping.put("null_value", "null");
				typeMapping.put("copy_to", "null");
				typeMapping.put("ignore_above", "2147483647");
				typeMapping.put("similarity", "BM25");
				typeMapping.put("eager_global_ordinals", "false");
				typeMapping.put("index_options", "positions");
				typeMapping.put("store", "true");
				typeMapping.put("fielddata", "true");
				typeMapping.put("doc_values", "true");
				typeMapping.put("norms", "true");
				typeMapping.put("boost", "1.0");
			}

		} catch (IOException e) {
			throw new EntityIndexingException("Communication Error checking if index exists: " + e.getMessage());
		}
		
		// crear indice si no existe

		// calcular el mapping de los tipos de entidad

		// enviar mapping a elastic

	}

	protected JSONEntityElastic createIEntity(String id, String type) {
		return new JSONEntityElastic(id, type);
	}

	protected void indexEntity(JSONEntityElastic elasticEntity, String indexName) throws EntityIndexingException {

		try {

			IndexRequest indexRequest = new IndexRequest(indexName);
			// set document ide
			indexRequest.id(elasticEntity.getId());
			indexRequest.source(jsonMapper.writeValueAsString(elasticEntity), XContentType.JSON);

			bulkRequest.add( indexRequest );

		} catch (JsonProcessingException e) {
			throw new EntityIndexingException("Error building Entity JSON :: " + e.getMessage() );
		}

	}

	private Multimap<String, Relation> getRelationMultimap(Entity entity) throws EntityRelationException {

		Multimap<String, Relation> relationsByName = ArrayListMultimap.create();

		for (Relation relation : entity.getFromRelations()) {
			RelationType rtype = entityDataService.getRelationTypeFromId(relation.getRelationTypeId());
			relationsByName.put(rtype.getName(), relation);
		}

		for (Relation relation : entity.getToRelations()) {
			RelationType rtype = entityDataService.getRelationTypeFromId(relation.getRelationTypeId());
			relationsByName.put(rtype.getName(), relation);
		}

		return relationsByName;
	}

	private void processFieldConfig(Entity entity, Multimap<String, Relation> relationsMap, FieldIndexingConfig config,
			JSONEntityElastic ientity) throws EntityIndexingException {


		String sourceFieldName = config.getSourceField();

		// tratar caso de lista de campos


		if (sourceFieldName == null)
			throw new EntityIndexingException(
					"Error Indexing Entity Field " + config.getName() + " source field is not defined");

		if (config.getSourceRelation() != null) {// is a relation indexing

			for (Relation relation : relationsMap.get(config.getSourceRelation())) {

				if (config.getSourceMember() != null) { // is a related entity field

					Entity relatedEntity = relation.getRelatedEntity(entity.getId());

					// add all occrs of the given source field from all entities of type source
					// member related by source relation
					processFieldOccurrence(relatedEntity.getFieldOccurrences(sourceFieldName), config, ientity);

				} else {// is a relation attribute

					processFieldOccurrence(relation.getFieldOccurrences(sourceFieldName), config, ientity);
				}
			}

		} else {// is a entity field
			processFieldOccurrence(entity.getFieldOccurrences(sourceFieldName), config, ientity);
		}
	}

	private void processFieldOccurrence(Collection<FieldOccurrence> occurrences, FieldIndexingConfig config,
			JSONEntityElastic ientity) {
		
		// if field filter is defined and the services is available, apply it
		if ( fieldOccurrenceFilterService != null && config.getFilter() != null ) {
			// get the params from the config
			Map<String, String> filterParams = config.getParams();

			// add the field name to the params
			filterParams.put("field", config.getSourceField());
			filterParams.put("subfield", config.getSourceSubfield());

			occurrences = fieldOccurrenceFilterService.filter(occurrences, config.getFilter(), filterParams);
		}

		for (FieldOccurrence occr : occurrences)
			try {

				String value;

				if (config.getSourceSubfield() != null)
					value = occr.getValue(config.getSourceSubfield());
				else
					value = occr.getValue();

				ientity.addFieldOccurrence(config.getName(), value);

//				if ( config.getSortable() ) 
//					ientity.addSortingFieldOccurrence(config.getName(), value);

			} catch (EntityRelationException e) {
				logger.error("Error indexing field: " + config.getSourceField() + " subfield: "
						+ config.getSourceSubfield() + "::" + e.getMessage());
			}
	}

	@Override
	public void delete(String entityId) throws EntityIndexingException {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteAll(Collection<String> idList) throws EntityIndexingException {
		// TODO Auto-generated method stub

	}
}