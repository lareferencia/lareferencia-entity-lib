
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

import org.lareferencia.core.entity.domain.Provenance;
import org.lareferencia.core.entity.repositories.jpa.ProvenanceRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public class ProvenanceStore  {

    ProvenanceRepository repository;

    public ProvenanceStore(ProvenanceRepository repository) {
        this.repository = repository;
    }

    /**
     * Load or create a Provenance record.
     * Runs within the caller's transaction context.
     * Removed synchronized to prevent deadlocks with database transactions.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Provenance loadOrCreate(String source, String record)  {

        Provenance createdProvenance = new Provenance(source,record);

        Optional<Provenance> optProvenance = repository.findById( createdProvenance.getId() );

        if ( optProvenance.isPresent() )
            return optProvenance.get();
        else {
            // Use save() instead of saveAndFlush() - flush will happen at transaction commit
            repository.save(createdProvenance);
            return createdProvenance;
        }
    }

    /**
     * Update the last update timestamp of a Provenance record.
     * This executes a @Modifying query which requires an active transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setLastUpdate(Provenance provenance, LocalDateTime lastUpdate) {
        repository.setLastUpdate(provenance.getId(), lastUpdate);
    }


}
