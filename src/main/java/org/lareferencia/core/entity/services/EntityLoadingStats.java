package org.lareferencia.core.entity.services;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class EntityLoadingStats {

    Long sourceEntitiesLoaded = 0L;
    Long entitiesCreated = 0L;
    Long entitiesDuplicated = 0L;
    Long sourceRelationsLoaded = 0L;
    Long relationsCreated = 0L;

    public EntityLoadingStats() {
        super();

    }

    void incrementSourceEntitiesLoaded() {
        this.sourceEntitiesLoaded++;
    }

    void incrementEntitiesCreated() {
        this.entitiesCreated++;
    }

    void incrementEntitiesDuplicated() {
        this.entitiesDuplicated++;
    }

    void incrementSourceRelationsLoaded() {
        this.sourceRelationsLoaded++;
    }

    void incrementRelationsCreated() {
        this.relationsCreated++;
    }

    public void add(EntityLoadingStats other) {
        this.sourceEntitiesLoaded += other.sourceEntitiesLoaded;
        this.entitiesCreated += other.entitiesCreated;
        this.entitiesDuplicated += other.entitiesDuplicated;
        this.sourceRelationsLoaded += other.sourceRelationsLoaded;
        this.relationsCreated += other.relationsCreated;
    }

    public void reset() {
        this.sourceEntitiesLoaded = 0L;
        this.entitiesCreated = 0L;
        this.entitiesDuplicated = 0L;
        this.sourceRelationsLoaded = 0L;
        this.relationsCreated = 0L;
    }

    @Override
    public String toString() {
        return "\nLoading Stats" +
                "\n\t" + sourceEntitiesLoaded + " source entities loaded" +
                "\n\t" + entitiesCreated + " entities created" +
                "\n\t" + entitiesDuplicated + " entities duplicated" +
                "\n\t" + sourceRelationsLoaded + " source relations loaded" +
                "\n\t" + relationsCreated + " relations created";
    }
}