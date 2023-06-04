package org.lareferencia.core.entity.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EntityIndexingStats {

    ConcurrentLinkedQueue<IndexingStat> indexingStats = new ConcurrentLinkedQueue<IndexingStat>();
    ConcurrentLinkedQueue<ErrorStat> errorStats = new ConcurrentLinkedQueue<ErrorStat>();

    public void addEntitySentToIndex(UUID uuid, Long type) {
        indexingStats.add( new IndexingStat(uuid, type) );
    }

    public void registerErrorStat(UUID uuid, String message) {
        errorStats.add( new ErrorStat(uuid, message) );
    }

    public int getAllSentEntitiesCount() {
        return indexingStats.size();
    }

    public int getUniqueSentEntitiesCount() {
        Set<UUID> uuids = new HashSet<UUID>();
        for (IndexingStat stat : indexingStats)
            uuids.add(stat.getUuid());
        return uuids.size();
    }

    public int getAllErrorsCount() {
        return errorStats.size();
    }

    public int getUniqueErrorsCount() {
        Set<UUID> uuids = new HashSet<UUID>();
        for (ErrorStat stat : errorStats)
            uuids.add(stat.getUuid());
        return uuids.size();
    }

    public Map<Long, Integer> getAllEntitiesCountByType() {
        Map<Long, Integer> result = new HashMap<Long, Integer>();
        for (IndexingStat stat : indexingStats) {
            Long type = stat.getType();
            if (result.containsKey(type)) {
                result.put(type, result.get(type) + 1);
            } else {
                result.put(type, 1);
            }
        }
        return result;
    }


    public void reset() {
        indexingStats.clear();
        errorStats.clear();
    }

    @AllArgsConstructor
    @Getter @Setter
    class IndexingStat {
       private UUID uuid;
       private Long type;
    }

    @AllArgsConstructor
    @Getter @Setter
    class ErrorStat {
        private UUID uuid;
        private String message;
    }

    @Override
    public String toString() {
        return  "\n\t" + "Total Sent Entities: " + getAllSentEntitiesCount() +
                "\n\t" + "Total Unique Sent Entities: " + getUniqueSentEntitiesCount() +
                "\n\t" + "Total Entities w/ Indexing Errors: " + getAllErrorsCount() +
                "\n\t" + "Total Unique Entities w/ Indexing Errors: " + getUniqueErrorsCount() +
                "\n";
    }
}
