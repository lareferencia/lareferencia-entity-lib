
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

package org.lareferencia.core.entity.indexing.solr;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.lareferencia.core.entity.indexing.service.AbstractPlainEntityIndexer;
import org.lareferencia.core.entity.indexing.service.IEntity;

public class EntityIndexerSolrImpl extends AbstractPlainEntityIndexer {

    private static Logger logger = LogManager.getLogger(EntityIndexerSolrImpl.class);

    private static final String SOLR_URL = "http://localhost:8983/solr/entity";
    private SolrClient solrClient;

    List<EntitySolr> entityBuffer = new LinkedList<EntitySolr>();

    public EntityIndexerSolrImpl() {
        this.solrClient = new HttpSolrClient.Builder(SOLR_URL).build();
    }

    @Override
    protected IEntity createIEntity(String id, String type) {
        return new EntitySolr(id, type);
    }

    @Override
    public void delete(String id) {
        logger.debug("Deleting entity: " + id);
        try {
            UpdateResponse response = solrClient.deleteById(id);
            solrClient.commit();
            logger.debug("Delete response: " + response);
        } catch (Exception e) {
            logger.error("Error deleting entity: " + id + " :: " + e.getMessage());
        }
    }

    @Override
    protected void saveIEntity(IEntity ientity) {
        entityBuffer.add((EntitySolr) ientity);
    }

    @Override
    public void flush() {
        try {
            List<UpdateResponse> responses = new LinkedList<>();
            for (EntitySolr entity : entityBuffer) {
                UpdateResponse response = solrClient.add(entity.toSolrInputDocument());
                responses.add(response);
            }
            solrClient.commit();
            logger.debug("Flush responses: " + responses);
            entityBuffer = new LinkedList<EntitySolr>();
        } catch (Exception e) {
            logger.error("Error flushing entities: " + e.getMessage());
        }
    }
}