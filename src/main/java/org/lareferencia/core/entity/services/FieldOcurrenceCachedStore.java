
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

import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.repositories.jpa.FieldOccurrenceRepository;

import org.springframework.transaction.PlatformTransactionManager;

public class FieldOcurrenceCachedStore extends ConcurrentCachedStore<Long, FieldOccurrence, FieldOccurrenceRepository> {

    public FieldOcurrenceCachedStore(FieldOccurrenceRepository repository, Integer capacity, PlatformTransactionManager transactionManager) {
        super(repository, capacity, false, 0);
    }

    /**
     * Load or create a FieldOccurrence. 
     * This method is NOT transactional - it runs within the caller's transaction context.
     * The actual persistence is handled by the parent class's put() method.
     */
    public FieldOccurrence loadOrCreate(FieldType type, IFieldValueInstance field) throws EntityRelationException {
        try {
            FieldOccurrence createdFieldOccr = type.buildFieldOccurrence();

            if (type.hasSubfields()) { // Complex Value field
                for (IFieldValueInstance subfield : field.getFields()) {
                    if (subfield.getValue() != null && !subfield.getValue().trim().isEmpty())
                        createdFieldOccr.addValue(subfield.getName(), subfield.getValue());
                }
            } else { // Single Value Field
                String fieldValue = (field.getValue() != null) ? field.getValue() : ""; // it can have an empty value
                createdFieldOccr.addValue(fieldValue, field.getLang());
            }

            // if is a preferred value, set it
            if (field.getPreferred())
                createdFieldOccr.setPreferred(field.getPreferred());

            // update the id
            createdFieldOccr.updateId();

            // Check cache first, then create if needed
            FieldOccurrence existingFieldOccr = this.get(createdFieldOccr.getId());
            if (existingFieldOccr == null) {
                this.put(createdFieldOccr.getId(), createdFieldOccr);
                return createdFieldOccr;
            } else {
                return existingFieldOccr;
            }
        } catch (Exception e) {
            throw new EntityRelationException("Error creating field occurrence: " + e.getMessage(), e);
        }
    }
}
