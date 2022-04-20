
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

import org.lareferencia.core.entity.domain.ICacheableNamedEntity;
import org.lareferencia.core.entity.repositories.jpa.NamedEntityJpaRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentCachedNamedStore<K,C extends ICacheableNamedEntity<K>,R extends NamedEntityJpaRepository<C, K>> extends ConcurrentCachedStore<K,C,R> {

    private ConcurrentHashMap<String, K> translationMap;

    public ConcurrentCachedNamedStore(R repository, Integer capacity, Boolean readOnly, Integer expireAfterWriteInMinutes) {
        super(repository, capacity, readOnly, expireAfterWriteInMinutes);
        translationMap = new ConcurrentHashMap<String,K>();
    }

    public C getByName(String name) throws CacheException {

        // Checks if the key is stored in the byName map
        K key = translationMap.get(name);
        if ( key != null )
            return this.get(key);
        else { // if not looks into the repository by name

            Optional<C> opt = this.repository.findOneByName(name);
            if ( opt.isPresent() ) { // is exists puts in the cache and in the byName Map
                C obj = opt.get();

                this.put(obj.getId(), obj);
                this.translationMap.put(obj.getName(), obj.getId());
                return obj;
            } else // else informs that that name does not exists in the database
                throw new CacheException("Object with name:" + name + " not found in database");
        }
    }

    @Override
    public void flush() {
        translationMap.clear();
        super.flush();
    }
}





