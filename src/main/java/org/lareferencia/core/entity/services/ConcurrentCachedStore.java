
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

import org.hibernate.Hibernate;
import org.lareferencia.core.entity.domain.ICacheableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ConcurrentCachedStore<K,C extends ICacheableEntity<K>,R extends JpaRepository<C, K>> {

    private final Boolean readOnly;
    protected final R repository;
    private final Cache<K,C> cache;

    public ConcurrentCachedStore(R repository,  Integer capacity, Boolean readOnly, Integer expireAfterWriteInMinutes) {

       this.repository = repository;
       this.readOnly = readOnly;

       Caffeine builder = Caffeine.newBuilder();

       builder.maximumSize(capacity);

       if ( expireAfterWriteInMinutes > 0 )
           builder.expireAfterWrite(expireAfterWriteInMinutes, TimeUnit.MINUTES);

       /*if ( !readOnly )
           builder.removalListener( (Object key, Object obj, RemovalCause cause) -> { if ( obj.) } );
       */
       cache = builder.build();


    }

    /**
     * Get entity from cache or load from database.
     * This is a read operation and runs within the caller's transaction context.
     */
    public C get(K key) {
        return cache.get(key, k -> {
            Optional<C> optObj = repository.findById(key);
            if (optObj.isPresent()) {
                @SuppressWarnings("unchecked")
                C entity = (C) Hibernate.unproxy(optObj.get());
                return entity;
            } else {
                return null;
            }
        });
    }

    /**
     * Put entity into cache and persist to database.
     * Runs within the caller's transaction context - does NOT create a new transaction.
     * This prevents transaction conflicts and rollback-only issues.
     */
    @Transactional(readOnly = false, propagation = Propagation.MANDATORY)
    public void put(K key, C obj) {

        if ( cache.getIfPresent(key) == null ) {

            if ( !readOnly) {
                // Use save() instead of saveAndFlush() to defer persistence
                // The flush will happen at transaction commit
                repository.save(obj);
                obj.markAsStored();
            }

            cache.put(key, obj);
        }

    }


    public void flush() {
        cache.invalidateAll();
    }


}
