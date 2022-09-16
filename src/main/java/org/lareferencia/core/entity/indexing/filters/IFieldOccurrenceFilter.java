package org.lareferencia.core.entity.indexing.filters;

import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;

import java.util.Collection;
import java.util.Map;

public interface IFieldOccurrenceFilter {


    public String getName();
    public Collection<FieldOccurrence> filter(Collection<FieldOccurrence> occurrences, Map<String,String> params);

}
