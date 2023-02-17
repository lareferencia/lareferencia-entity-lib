
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
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;

import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContexts;
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
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.springframework.context.ApplicationContext;

import javax.net.ssl.SSLContext;

public class JSONElasticEntityIndexerImpl implements IEntityIndexer {

	private static Logger logger = LogManager.getLogger(JSONElasticEntityIndexerImpl.class);

	private IndexingConfiguration indexingConfiguration;
	private String indexingConfigFilename;

	//List<JSONEntityElastic> entityBuffer = new LinkedList<JSONEntityElastic>();

	Map<String, EntityIndexingConfig> configsByEntityType;

	@Autowired
	EntityDataService entityDataService;

	ObjectMapper jsonMapper;

	RestHighLevelClient elasticClient = null;

	@Value("${elastic.host:localhost}")
	private String host;

	@Value("${elastic.port:9200}")
	private Integer port;

	@Value("${elastic.username:admin}")
	private String username;

	@Value("${elastic.password:admin}")
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
	/**
	 * This method will be called in the beginning of the indexing process to load the configuration and initialize the indexer
	 */
	public void setConfig(String configFilePath) throws EntityIndexingException {

		logger.info("Loading indexing config from: " + configFilePath);

		// load indexing configuration
		try {
			this.indexingConfigFilename = configFilePath ;
			indexingConfiguration = IndexingConfiguration.loadFromXml(configFilePath);
		} catch (Exception e) {
			throw new EntityIndexingException(" Error loading indexing configuration from file: " + configFilePath + " " + e.getMessage());
		}

		logger.info("Processing Elastic Indexer Config File: " + indexingConfigFilename);

		// load filters for field occurrence filtering
		loadOccurFilters();

		// build elastic client
		elasticClient = buildElasticRestClient();

		// create index mappings
		createIndexMappings();

		// create map of entity types to indexing configs
		configsByEntityType = new HashMap<String, EntityIndexingConfig>();
		for (EntityIndexingConfig entityIndexingConfig : indexingConfiguration.getEntityIndices())
			configsByEntityType.put(entityIndexingConfig.getEntityType(), entityIndexingConfig);

		// create json mapper with custom serializer for JSONEntityElastic
		jsonMapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(JSONEntityElastic.class, new JSONEntityElasticSerializer());
		jsonMapper.registerModule(module);

		// create first bulk request
		resetBulkRequest();

		logger.info("Elastic Indexer Config File: " + indexingConfigFilename + " processed successfully");
	}


	/**
	 * Creates the indices and mappings for the entities to be indexed using the configuration file as a reference
	 */
	private void createIndexMappings() throws EntityIndexingException {
		try {
			for (EntityIndexingConfig entityIndexingConfig : indexingConfiguration.getEntityIndices()) {
				// create index if not exists, calculate and set mapping
				createOrUpdateIndexMapping(entityIndexingConfig);
			}
		} catch (Exception e) {
			throw new EntityIndexingException(" Error when creating index mapping from file" + indexingConfigFilename + " :: "  + e.getMessage());
		}
	}

	
	private void resetBulkRequest() {
		bulkRequest = new BulkRequest();
		bulkRequest.timeout("5m");
	}

	/**
	 * loads the field occurrence filters from spring context and injects them into the service
	 */
	private void loadOccurFilters() {
		// Load dynamic field occurrence filters from spring context
		try {
			// get the service from spring context
			fieldOccurrenceFilterService = FieldOccurrenceFilterService.getServiceInstance( context );
			if ( fieldOccurrenceFilterService != null )
				// load the filters from spring context
				fieldOccurrenceFilterService.loadFiltersFromApplicationContext(context);

			logger.debug( "fieldOccurrenceFilterService: " + fieldOccurrenceFilterService.getFilters().toString() );
		} catch (Exception e) {
			logger.warn("Error loading field occurrence filters: " + e.getMessage());
		}
	}

	/**
	 * Builds the elastic rest client
	 * @throws EntityIndexingException
	 */
	private RestHighLevelClient buildElasticRestClient() throws EntityIndexingException {

		RestHighLevelClient localClient = null;

		// Build the rest client for elasticsearch/opensearch connection
		try {

			// create a credentials provider to authenticate with the given username and password
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username.trim(), password.trim()));

			// create a trust all strategy to accept any certificate
			final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();

			//Create a client builder with the given host and port, and the ssl context and credentials provider
			RestClientBuilder builder = RestClient.builder(new HttpHost( host.trim(), port, useSSL ? "https" : "http"))
					.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
						@Override
						public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {

							HttpAsyncClientBuilder builder = httpClientBuilder;

							// if ssl is required, we need to set the ssl context
							if ( useSSL )
								builder = httpClientBuilder.setSSLContext(sslContext);
							else
							// if ssl is not required, we just copy the original builder
								builder = httpClientBuilder;

							// if authentication is required, we need to set the credentials provider
							if ( authenticate )
								builder = builder.setDefaultCredentialsProvider(credentialsProvider);

							return builder;
						}
					});

			// create the client
			localClient = new RestHighLevelClient(builder);

			// check if the connection is ok
			localClient.ping(RequestOptions.DEFAULT);
            logger.info("Elasticsearch/Opensearch client created: " + host + ":" + port + (useSSL ? " using SSL" : " ") + (authenticate ? " using authentication" : ""));

			return localClient;

		} catch (Exception e) {
		    logger.error("Error connecting elasticsearch/opensearch:" + host + ":" + port + " :: " + e.getMessage());
			throw new EntityIndexingException(" Elastic Client creation error ");
		}
	}

	@Override
	/**
	 * Indexes the given entity in elasticsearch bulk request that will be executed later
	 * @param entity
	 * @throws EntityIndexingException
	 */
	public void index(Entity entity) throws EntityIndexingException {

		try {

			// get the entity type
			EntityType type = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());
			// get the entity indexing config for the entity type
			EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(type.getName());

			// if there is no config for the entity type, throw an exception
			if (entityIndexingConfig == null)
				throw new EntityIndexingException(
						"Error indexing entity: " + entity.getId() + " " + this.indexingConfigFilename
								+ " does not contain a indexing config for " + type.getName() + " EntityType");

			// get the relations for the entity
			Multimap<String, Relation> relationsMap = this.getRelationMultimap(entity);

			// create the elastic entity from the entity and the relations
			JSONEntityElastic elasticEntity = createElasticEntity(entityIndexingConfig, entity, relationsMap);

			// get the nested entities and create the related elastic entities
			for (EntityIndexingConfig nestedEntityConfig : entityIndexingConfig.getIndexNestedEntities()) {

				String relationName = nestedEntityConfig.getEntityRelation();
				// get the relations for the nested entity
				for (Relation relation : relationsMap.get(relationName)) {
					//
					Entity relatedEntity = relation.getRelatedEntity(entity.getId());
					// get the relations for the related entity
					Multimap<String, Relation> relatedEntityRelationsMap = this.getRelationMultimap(relatedEntity);
					// create the related elastic entity
					JSONEntityElastic relatedElasticEntity = createElasticEntity(nestedEntityConfig, relatedEntity,
							relatedEntityRelationsMap);
					// add the related elastic entity to the parent entity
					elasticEntity.addRelatedEntity(nestedEntityConfig.getName(), relatedElasticEntity);
				}
			}

			// index the entity
			indexEntity(elasticEntity, entityIndexingConfig.getName());

		} catch (Exception e) {
			throw new EntityIndexingException("Error indexing entity: " + entity.toString() + " Error was: " + e.getMessage());
		}

	}

	/**
	 * Create the elastic entity from the entity and the relations
	 *
	 * @param config
	 * @param entity
	 * @param relationsMap
	 * @throws EntityIndexingException
	 */
	public JSONEntityElastic createElasticEntity(EntityIndexingConfig config, Entity entity,
			Multimap<String, Relation> relationsMap) throws EntityIndexingException {

		// create the elastic entity
		JSONEntityElastic jsonEntityElastic = new JSONEntityElastic();

		try {
			// set the id based on entity uuid
			jsonEntityElastic.setId(entity.getId().toString());

			// set the entity type
			if ( config.getindexEntityType() )
				jsonEntityElastic.setType(entityDataService.getEntityTypeFromId(entity.getEntityTypeId()).getName());

			// set the entity semantic ids
			if ( config.getIndexSemanticIds() ) {
				for (SemanticIdentifier semanticId : entity.getSemanticIdentifiers())
					jsonEntityElastic.addSemanticId(semanticId.getIdentifier());
			}

			// set the entity fields
			for (FieldIndexingConfig fieldConfig : config.getIndexFields())
				processFieldConfig(entity, relationsMap, fieldConfig, jsonEntityElastic);

		} catch (Exception e) {
			throw new EntityIndexingException("Error creating JSONElasticEntity: " + config.getName() + " :: " + config.getEntityType() + " from entity: " + entity.getId() + " :: " + e.getMessage() );
		}
		return jsonEntityElastic;
	}

	@Override
	/**
	 * Flush the bulk request to the elastic client
	 * @throws EntityIndexingException
	 */
	public void flush() throws EntityIndexingException {

		Boolean retry = true;
		int retries = 0;
		int millis = 2500;

		while ( retry ) {

			try {
				BulkResponse bulkResponse = elasticClient.bulk(bulkRequest, RequestOptions.DEFAULT);
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
		resetBulkRequest();
	}



	private Map<String, Object> createTypeMapping(String type) {

		Map<String, Object> fieldMapping = new HashMap<String, Object>();
		fieldMapping.put("type", type);

		return fieldMapping;
	}

	private void createOrUpdateIndexMapping( EntityIndexingConfig entityIndexingConfig ) throws EntityIndexingException {

		// create mapping based on entity indexing config
		HashMap<String, Object> typesMapping = new HashMap<String, Object>();
		HashMap<String, Object> mapping = new HashMap<String, Object>();
		mapping.put("properties", typesMapping);

		// add fields to mapping
		for ( FieldIndexingConfig fieldConfig : entityIndexingConfig.getIndexFields() )
			typesMapping.put(fieldConfig.getName(), createTypeMapping(fieldConfig.getType()));
		// add id field to mapping
		typesMapping.put("id", createTypeMapping("keyword"));

		// add nested entities to mapping
		entityIndexingConfig.getIndexNestedEntities().forEach( nestedEntityConfig -> {
			HashMap<String, Object> nestedTypesMapping = new HashMap<String, Object>();
			HashMap<String, Object> nestedMapping = new HashMap<String, Object>();
			nestedMapping.put("properties", nestedTypesMapping);
			typesMapping.put(nestedEntityConfig.getName(), nestedMapping);

			// add fields to mapping
			for ( FieldIndexingConfig fieldConfig : nestedEntityConfig.getIndexFields() )
				nestedTypesMapping.put(fieldConfig.getName(), createTypeMapping(fieldConfig.getType()));

			// add id field to mapping
			nestedTypesMapping.put("id", createTypeMapping("keyword"));
		});

		// check if index exists
		try {
			Boolean indexExists = elasticClient.indices().exists(new GetIndexRequest(entityIndexingConfig.getName() ), RequestOptions.DEFAULT);

			if ( indexExists ) {
				logger.warn("Index " + entityIndexingConfig.getName() + " already exists. Is not possible to update mapping !!!");

			} else {
				logger.info("Index " + entityIndexingConfig.getName() + " does not exist, creating it. With mapping: " + mapping.toString() + "");

				// create index
				CreateIndexRequest createIndexRequest = new CreateIndexRequest( entityIndexingConfig.getName() );
				//createIndexRequest.settings(Settings.builder() //Specify in the settings how many shards you want in the index.
				//		.put("index.number_of_shards", 4)
				//		.put("index.number_of_replicas", 3)
				//);

				createIndexRequest.mapping(mapping);
				CreateIndexResponse createIndexResponse = elasticClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);

				logger.info("Index " + entityIndexingConfig.getName() + " created: " + createIndexResponse.isAcknowledged() + "");
			}

		} catch (IOException e) {
			throw new EntityIndexingException("Error trying index creation / mapping creation: " + entityIndexingConfig.getName() + " :: " + e.getMessage());
		}
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