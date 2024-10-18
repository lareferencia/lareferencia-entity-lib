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

package org.lareferencia.core.entity.search.solr;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.lareferencia.core.entity.indexing.service.IEntity;
import org.lareferencia.core.entity.indexing.solr.EntitySolr;
import org.lareferencia.core.entity.search.service.IEntitySearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class EntitySearchServiceSolrImpl implements IEntitySearchService {

    private static final Logger logger = LogManager.getLogger(EntitySearchServiceSolrImpl.class);
    private static final String SOLR_URL = "http://localhost:8983/solr/entity";
    private SolrClient solrClient;

    public EntitySearchServiceSolrImpl() {
        this.solrClient = new HttpSolrClient.Builder(SOLR_URL).build();
    }

    @Override
    public Page<? extends IEntity> listEntitiesByType(String entityTypeName, Pageable pageable) {
        SolrQuery query = new SolrQuery();
        query.setQuery(EntitySolr.TYPE_FIELD_NAME + ":" + entityTypeName);
        query.setStart((int) pageable.getOffset());
        query.setRows(pageable.getPageSize());

        try {
            QueryResponse response = solrClient.query(query);
            SolrDocumentList documents = response.getResults();
            List<EntitySolr> entities = new ArrayList<>();
            documents.forEach(doc -> entities.add(EntitySolr.fromSolrDocument(doc)));
            return new PageImpl<>(entities, pageable, documents.getNumFound());
        } catch (Exception e) {
            logger.error("Error listing entities by type: " + entityTypeName + " :: " + e.getMessage());
            return Page.empty();
        }
    }

    @Override
    public Page<? extends IEntity> searchEntitiesByTypeAndFields(String entityTypeName, List<String> fieldExpressions, Pageable pageable) {
        SolrQuery query = new SolrQuery();
        query.setQuery(EntitySolr.TYPE_FIELD_NAME + ":" + entityTypeName);
        query.setStart((int) pageable.getOffset());
        query.setRows(pageable.getPageSize());

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(EntitySolr.TYPE_FIELD_NAME).append(":").append(entityTypeName);

        if (fieldExpressions != null) {
            for (String expression : fieldExpressions) {
                String[] parsed = expression.split(":");
                String fieldName = EntitySolr.DYNAMIC_FIELD_PREFIX + parsed[0];
                queryBuilder.append(" AND ").append(fieldName).append(":").append(parsed[1]);
            }
        }

        query.setQuery(queryBuilder.toString());

        try {
            QueryResponse response = solrClient.query(query);
            SolrDocumentList documents = response.getResults();
            List<EntitySolr> entities = new ArrayList<>();
            documents.forEach(doc -> entities.add(EntitySolr.fromSolrDocument(doc)));
            return new PageImpl<>(entities, pageable, documents.getNumFound());
        } catch (Exception e) {
            logger.error("Error searching entities by type and fields: " + entityTypeName + " :: " + e.getMessage());
            return Page.empty();
        }
    }
}