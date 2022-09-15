package org.lareferencia.core.entity.indexing.filters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LongestStringFieldOccurrenceFilter implements IFieldOccurrenceFilter {

    private static Logger logger = LogManager.getLogger(LongestStringFieldOccurrenceFilter.class);

    public String getName() {
        return "longest-string";
    }

    public Collection<FieldOccurrence> filter(Collection<FieldOccurrence> occurrences, FieldIndexingConfig config) {


        Collection<FieldOccurrence> filteredOccurrences = occurrences.stream()
                .filter(occurrence -> getLength(occurrence) == occurrences.stream()
                        .mapToInt(occ -> getLength(occ))
                        .max().getAsInt())
                .collect(Collectors.toList());

        if (config.getFilterOneValue())
            filteredOccurrences = filteredOccurrences.stream().limit(1).collect(Collectors.toList());

        return filteredOccurrences;
    }


    private int getLength(FieldOccurrence a) {
        try {
            return a.getValue().length();
        } catch (EntityRelationException e) {
            logger.error("Error filtering occurrences " + this.getName() + " ", e);
        }
        return 0;
    }
}
