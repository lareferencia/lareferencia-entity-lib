
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

package org.lareferencia.core.entity.domain;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.data.rest.webmvc.spi.BackendIdConverter;
import org.springframework.stereotype.Component;

@Component
public class RelationIdConverter implements BackendIdConverter {

	@Override
    public Serializable fromRequestId(String id, Class<?> entityType) {
        String[] parts = id.split("_");
        return new RelationId(Long.valueOf(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2]));
    }

    @Override
    public String toRequestId(Serializable source, Class<?> entityType) {
        RelationId id = (RelationId)source;
        return String.format("%s_%s_%s", id.relationTypeId.toString(), id.fromEntityId.toString(), id.toEntityId.toString());
    }

    @Override
    public boolean supports(Class<?> type) {
        return Relation.class.equals(type);
    }
}
