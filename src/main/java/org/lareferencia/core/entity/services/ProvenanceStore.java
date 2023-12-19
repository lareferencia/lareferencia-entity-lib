
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
import org.lareferencia.core.entity.domain.ProvenanceId;
import org.lareferencia.core.entity.repositories.jpa.ProvenanceRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public class ProvenanceStore  {

    ProvenanceRepository repository;

    public ProvenanceStore(ProvenanceRepository repository) {
        this.repository = repository;
    }

    public synchronized Provenance loadOrCreate(String source, String record)  {

        Provenance createdProvenance = new Provenance(source,record);

        Optional<Provenance> optProvenance = repository.findById( new ProvenanceId(source, record) );

        if ( optProvenance.isPresent() )
            return optProvenance.get();
        else {
            repository.saveAndFlush(createdProvenance);
            return createdProvenance;
        }
    }


    public void setLastUpdate(Provenance provenance, LocalDateTime lastUpdate) {

        provenance.setLastUpdate(lastUpdate);
        repository.saveAndFlush(provenance);

    }


}
