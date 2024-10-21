package org.lareferencia.core.entity.domain;

import java.util.Set;

public class Loaded<T> {

    T entity;

    boolean created = false;

    public Loaded(T entity) {
        this.entity = entity;
    }

    public Loaded(T entity, boolean created) {
        this.entity = entity;
        this.created = created;
    }

    public T get() {
        return entity;
    }

    public Boolean wasCreated() {
        return created;
    }

    public void addSemanticIdentifiers(Set<SemanticIdentifier> semanticIdentifiers) {
    }
    
}
