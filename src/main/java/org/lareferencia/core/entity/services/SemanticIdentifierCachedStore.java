
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

import org.lareferencia.core.entity.domain.Loaded;
import org.lareferencia.core.entity.domain.SemanticIdentifier;
import org.lareferencia.core.entity.repositories.jpa.SemanticIdentifierRepository;


public class SemanticIdentifierCachedStore extends ConcurrentCachedStore<String, SemanticIdentifier, SemanticIdentifierRepository> {

    public SemanticIdentifierCachedStore(SemanticIdentifierRepository repository, Integer capacity) {
        super(repository, capacity, false, 0);
    }

    public synchronized Loaded<SemanticIdentifier> loadOrCreate(String semantiIdentifier, Boolean readOnly) {

        SemanticIdentifier existingSemanticIdentifier = this.get( semantiIdentifier );

        if ( existingSemanticIdentifier == null ) {

            SemanticIdentifier createdSemanticIdentifier = new SemanticIdentifier(semantiIdentifier);

            // if readOnly is true, we don't persist the new semantic identifier by calling putWithoutPersist
            if (readOnly)
                this.putWithoutPersist(semantiIdentifier, createdSemanticIdentifier);
            else
                this.put(semantiIdentifier, createdSemanticIdentifier);
            
            return new Loaded<SemanticIdentifier>(createdSemanticIdentifier, true);
        }
        else {
            return new Loaded<SemanticIdentifier>(existingSemanticIdentifier, false);
        }
    }

    public synchronized Loaded<SemanticIdentifier> loadOrCreate(String semantiIdentifier) {
        return loadOrCreate(semantiIdentifier, false);
    }


}
