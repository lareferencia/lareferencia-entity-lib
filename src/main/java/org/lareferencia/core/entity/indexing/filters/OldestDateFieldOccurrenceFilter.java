package org.lareferencia.core.entity.indexing.filters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;
import org.lareferencia.core.util.date.DateHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OldestDateFieldOccurrenceFilter implements IFieldOccurrenceFilter {

    @Autowired
    private DateHelper dateHelper;

    private static Logger logger = LogManager.getLogger(OldestDateFieldOccurrenceFilter.class);

    public String getName() { return "oldest-date"; }

    public Collection<FieldOccurrence> filter(Collection<FieldOccurrence> occurrences,  FieldIndexingConfig config) {


        Comparator<LocalDateTime> compareLocalDateTimes = (LocalDateTime o1, LocalDateTime o2) -> {
            if (o1.isBefore(o2)) {
                return -1;
            } else if (o1.isAfter(o2)) {
                return 1;
            } else {
                return 0;
            }
        };

        Collection<FieldOccurrence> filteredOccurrences = occurrences.stream()
                .filter(occurrence -> getLocalDateTime(occurrence).equals(occurrences.stream()
                        .map(occ -> getLocalDateTime(occ))
                        .min(compareLocalDateTimes).get()))
                .collect(Collectors.toList());


        if (config.getFilterOneValue())
            filteredOccurrences = filteredOccurrences.stream().limit(1).collect(Collectors.toList());
        
        return filteredOccurrences;
    }



    private LocalDateTime getLocalDateTime(FieldOccurrence a) {

        try {
            return dateHelper.parseDate( a.getValue() );
        } catch (EntityRelationException e) {
            logger.error("Error filtering occurrences " + this.getName() + " ", e);
        }
        return LocalDateTime.now();
    }
}
