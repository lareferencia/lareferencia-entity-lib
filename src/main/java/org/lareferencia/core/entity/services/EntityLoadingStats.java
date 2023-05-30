package org.lareferencia.core.entity.services;

import lombok.Getter;
import lombok.ToString;

@ToString
public class EntityLoadingStats {

    @Getter
    private int sourceEntities;

    @Getter
    private int entities;

    @Getter
    private int duplicationsFound;

    @Getter
    private int sourceRelations;

    @Getter
    private int relations;

    public EntityLoadingStats() {
        this.sourceEntities = 0;
        this.entities = 0;
        this.sourceRelations = 0;
        this.relations = 0;
        this.duplicationsFound = 0;
    }

    public void incrementSourceEntities() {
        this.sourceEntities++;
    }

    public void incrementEntities() {
        this.entities++;
    }

    public void incrementSourceRelations() {
        this.sourceRelations++;
    }

    public void incrementRelations() {
        this.relations++;
    }

    public void incrementDuplicationsFound() {
        this.duplicationsFound++;
    }

    public void add(EntityLoadingStats other) {
        this.sourceEntities += other.sourceEntities;
        this.entities += other.entities;
        this.sourceRelations += other.sourceRelations;
        this.relations += other.relations;
        this.duplicationsFound += other.duplicationsFound;
    }
}