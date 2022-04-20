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

import java.util.List;

import org.lareferencia.core.entity.indexing.service.IEntity;
import org.lareferencia.core.entity.indexing.solr.EntitySolr;
import org.lareferencia.core.entity.repositories.solr.EntitySolrRepository;
import org.lareferencia.core.entity.search.service.IEntitySearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.query.Criteria;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.result.ScoredPage;
import org.springframework.stereotype.Service;

@Service
public class EntitySearchServiceSolrImpl implements IEntitySearchService {

    @Autowired
    private EntitySolrRepository repository;

    @Autowired
    private SolrTemplate solrTemplate;

    @Override
    public Page<? extends IEntity> listEntitiesByType(String entityTypeName, Pageable pageable) {

        return repository.findEntitiesByEntityType(entityTypeName, pageable);

    }

    @Override
    public Page<? extends IEntity> searchEntitiesByTypeAndFields(String entityTypeName, List<String> fieldExpressions, Pageable pageable) {

       Criteria conditions = new Criteria(EntitySolr.TYPE_FIELD_NAME).is(entityTypeName);

        conditions = addSearchConditions(conditions, EntitySolr.DYNAMIC_FIELD_PREFIX, fieldExpressions);

        SimpleQuery search = new SimpleQuery(conditions);
        search.setPageRequest(pageable);

        ScoredPage<EntitySolr> resultsPage = solrTemplate.queryForPage(EntitySolr.COLLECTION, search, EntitySolr.class);

        return resultsPage;
    }

  

    private Criteria addSearchConditions(Criteria conditions, String fieldPrefix, List<String> expressions) {

        if (expressions != null) {
            for (String expresion : expressions) {

                String[] parsed = expresion.split(":");
                String fieldName = fieldPrefix + parsed[0];
                Criteria criteria = new Criteria(fieldName).contains(parsed[1]);

                conditions = conditions.and(criteria);

            }
        }

        return conditions;
    }


}
