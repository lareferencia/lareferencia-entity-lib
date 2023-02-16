
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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;

import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
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

	RestHighLevelClient client = null;

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
	public void setConfig(String configFilePath) throws EntityIndexingException {

		logger.info("Loading indexing config from: " + configFilePath);

		// Load dinamic field occurrence filters from spring context
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

		// Build the rest client for elasticsearch/opensearch connection
		try {

			// if the opensearch/elasticsearch connection is secure, we need to use the https protocol and a valid certificate
			if ( useSSL ) {

				// create a trust all strategy to accept any certificate
				final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();

				// if authentication is required, we need to set the credentials provider
				final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
				credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username.trim(), password.trim()));

				//Create a client builder with the given host and port, and the ssl context and credentials provider
				RestClientBuilder builder = RestClient.builder(new HttpHost( host.trim(), port, "https"))
						.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
							@Override
							public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
								return httpClientBuilder.setSSLContext(sslContext).setDefaultCredentialsProvider(credentialsProvider);
							}
						});

				// create the client
				client = new RestHighLevelClient(builder);

//				client = new RestHighLevelClient(RestClient
//						//port number is given as 443 since its https schema
//						.builder(new HttpHost(host.trim(), port, "https"))
//						.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
//							@Override
//							public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
//
//								// if authentication is required, we need to set the credentials provider
//								if ( authenticate)
//									return httpClientBuilder
//											.setSSLContext(sslContext)
//											.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
//											.setDefaultCredentialsProvider(credentialsProvider);
//
//								else
//									return httpClientBuilder
//										.setSSLContext(sslContext)
//										.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
//							}
//						})
//						.setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
//							@Override
//							public RequestConfig.Builder customizeRequestConfig(
//									RequestConfig.Builder requestConfigBuilder) {
//								return requestConfigBuilder.setConnectTimeout(5000)
//										.setSocketTimeout(120000);
//							}
//						}));


			} else { // if the connection is not secure, we use the http protocol

				RestClientBuilder builder = RestClient.builder( new HttpHost(host.trim(), port ) );
				client = new RestHighLevelClient(builder);
			}

			// check if the connection is ok
			client.ping(RequestOptions.DEFAULT);
			
            logger.info("Elasticsearch/Opensearch connection created: " + host + ":" + port + " using SSL");


		} catch (Exception e) {
		    logger.error("Error connecting elasticsearch/opensearch:" + host + ":" + port + " :: " + e.getMessage());
			throw new EntityIndexingException("Connection Error");
		}

		this.indexingConfigFilename = configFilePath ;

		try {
			indexingConfiguration = IndexingConfiguration.loadFromXml(configFilePath);

			configsByEntityType = new HashMap<String, EntityIndexingConfig>();

			for (EntityIndexingConfig entityIndexingConfig : indexingConfiguration.getEntityIndices()) {

				// put entity indexing config in the map for later use
				configsByEntityType.put(entityIndexingConfig.getEntityType(), entityIndexingConfig);

				// create index if not exists, calculate and set mapping
				createOrUpdateIndexMapping(entityIndexingConfig);
			}

			logger.info("Elastic Indexer Config File: " + indexingConfigFilename + " loaded");

		} catch (Exception e) {
		    logger.error("Error procesing index configuration: " + e.getMessage());
			throw new EntityIndexingException("Error loading Elastic Indexer Config File: " + indexingConfigFilename);
		}


		// JSON MAPPER
		jsonMapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(JSONEntityElastic.class, new JSONEntityElasticSerializer());
		jsonMapper.registerModule(module);

		// String serialized = mapper.writeValueAsString(myItem);
		bulkRequest = new BulkRequest();
		bulkRequest.timeout("2m");
	}

	@Override
	public void index(Entity entity) throws EntityIndexingException {

		try {

			EntityType type = entityDataService.getEntityTypeFromId(entity.getEntityTypeId());

			EntityIndexingConfig entityIndexingConfig = configsByEntityType.get(type.getName());

			if (entityIndexingConfig == null)
				throw new EntityIndexingException(
						"Error indexing entity: " + entity.getId() + " " + this.indexingConfigFilename
								+ " does not contain a indexing config for " + type.getName() + " EntityType");

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
		for ( FieldIndexingConfig fieldIndexingConfig : entityIndexingConfig.getIndexFields() )
			typesMapping.put( fieldIndexingConfig.getName(),  createTypeMapping(fieldIndexingConfig.getType() ) );


		// check if index exists
		try {
			Boolean indexExists = client.indices().exists(new GetIndexRequest(entityIndexingConfig.getName() ), RequestOptions.DEFAULT);

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
				CreateIndexResponse createIndexResponse = client.indices().create(createIndexRequest, RequestOptions.DEFAULT);

				logger.info("Index " + entityIndexingConfig.getName() + " created: " + createIndexResponse.isAcknowledged() + "");
			}

		} catch (IOException e) {
			throw new EntityIndexingException("Communication Error checking if index exists: " + e.getMessage());
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