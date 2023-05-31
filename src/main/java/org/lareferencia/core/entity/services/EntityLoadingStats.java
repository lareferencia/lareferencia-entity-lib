package org.lareferencia.core.entity.services;

import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@ToString
public class EntityLoadingStats extends HashMap<String, Long> {

    public EntityLoadingStats() {

        super();

        this.put("sourceEntities", 0L);
        //this.put("updatedSourceEntities", 0L);
        this.put("entities", 0L);
        this.put("duplicationsFound", 0L);
        this.put("sourceRelations", 0L);
        //this.put("relations", 0L);

    }

    public void incrementSourceEntities()  {
        this.put("sourceEntities", this.get("sourceEntities") + 1L);
    }

    public void incrementEntities()  {
        this.put("entities", this.get("entities") + 1L);
    }

    public void incrementDuplicationsFound()  {
        this.put("duplicationsFound", this.get("duplicationsFound") + 1L);
    }

    public void incrementSourceRelations()  {
        this.put("sourceRelations", this.get("sourceRelations") + 1L);
    }

    public void incrementRelations()  {
        this.put("relations", this.get("relations") + 1L);
    }

    public void add(EntityLoadingStats other) {
        this.put("sourceEntities", this.get("sourceEntities") + other.get("sourceEntities"));
        this.put("entities", this.get("entities") + other.get("entities"));
        this.put("duplicationsFound", this.get("duplicationsFound") + other.get("duplicationsFound"));
        this.put("sourceRelations", this.get("sourceRelations") + other.get("sourceRelations"));
        this.put("relations", this.get("relations") + other.get("relations"));
    }

}