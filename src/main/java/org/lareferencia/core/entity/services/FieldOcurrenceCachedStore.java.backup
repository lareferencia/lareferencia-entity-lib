
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class FieldOcurrenceCachedStore extends ConcurrentCachedStore<Long, FieldOccurrence, FieldOccurrenceRepository> {

    private final PlatformTransactionManager transactionManager;

    public FieldOcurrenceCachedStore(FieldOccurrenceRepository repository, Integer capacity, PlatformTransactionManager transactionManager) {
        super(repository, capacity, false, 0);
        this.transactionManager = transactionManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized FieldOccurrence loadOrCreate(FieldType type, IFieldValueInstance field) throws EntityRelationException {
        TransactionStatus transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition());
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

            FieldOccurrence existingFieldOccr = this.get(createdFieldOccr.getId());
            if (existingFieldOccr == null) {
                this.put(createdFieldOccr.getId(), createdFieldOccr);
                transactionManager.commit(transactionStatus);
                return createdFieldOccr;
            } else {
                transactionManager.rollback(transactionStatus);
                return existingFieldOccr;
            }
        } catch (Exception e) {
            if (!transactionStatus.isCompleted()) {
                transactionManager.rollback(transactionStatus);
            }
            throw new EntityRelationException("Error during transaction", e);
        }
    }
}
