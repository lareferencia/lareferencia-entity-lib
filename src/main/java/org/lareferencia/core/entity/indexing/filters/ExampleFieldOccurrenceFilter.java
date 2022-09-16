package org.lareferencia.core.entity.indexing.filters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExampleFieldOccurrenceFilter implements IFieldOccurrenceFilter {

    public static final String NAME = "example";

    private static Logger logger = LogManager.getLogger(ExampleFieldOccurrenceFilter.class);

    public String getName() { return NAME; }

    public Collection<FieldOccurrence> filter(Collection<FieldOccurrence> occurrences, Map<String,String> params) {

       int filter_limit = params.containsKey("filter-limit") ? Integer.parseInt(params.get("filter-limit")) : 1;

        Collection<FieldOccurrence> filteredOccurrences = occurrences.stream()
                .filter(occurrence -> getLength(occurrence, params) == occurrences.stream()
                        .mapToInt(occ -> getLength(occ, params))
                        .max().getAsInt())
                .collect(Collectors.toList());

        // limit the number of occurrences to filter_limit
        filteredOccurrences = filteredOccurrences.stream().limit(filter_limit).collect(Collectors.toList());

        return filteredOccurrences;
    }


    private int getLength(FieldOccurrence a, Map<String,String> params) {

        String subfield = params.containsKey("subfield") ? params.get("subfield") : null;

        try {
            if (subfield != null)
                return a.getValue(subfield).length();
            else
                return a.getValue().length();

        } catch (EntityRelationException e) {
            logger.error("Error filtering occurrences " + this.getName() + " ", e);
        }
        return 0;
    }
}
