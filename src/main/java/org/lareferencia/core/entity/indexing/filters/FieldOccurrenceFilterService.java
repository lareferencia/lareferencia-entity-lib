package org.lareferencia.core.entity.indexing.filters;

import lombok.Getter;
import lombok.Setter;
import org.lareferencia.core.entity.domain.FieldOccurrence;
import org.lareferencia.core.entity.indexing.nested.config.FieldIndexingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FieldOccurrenceFilterService {

	/** This static method helps clients to check if the service is available in the application contexts **/
	public static FieldOccurrenceFilterService getServiceInstance(ApplicationContext applicationContext) {

		try {
			String[] beanNames = applicationContext.getBeanNamesForType(FieldOccurrenceFilterService.class);

			if (beanNames.length > 0 ) {
				return applicationContext.getBean(FieldOccurrenceFilterService.class);
			}

		} catch (Exception e) {
			return null;
		}

		return null;
	}

	FieldOccurrenceFilterService() {
		// empty constructor
	}

	@Setter
	@Getter
	Map<String, IFieldOccurrenceFilter> filters = new HashMap<String, IFieldOccurrenceFilter>();

	/**
	 * This method is used load all the filters available in the application context
	 * @param applicationContext
	 */
	public void loadFiltersFromApplicationContext(ApplicationContext applicationContext) {
		String[] beanNames = applicationContext.getBeanNamesForType(IFieldOccurrenceFilter.class);

		Arrays.asList(beanNames).forEach( beanName -> {
			IFieldOccurrenceFilter filter = applicationContext.getBean(beanName, IFieldOccurrenceFilter.class);
			filters.put(filter.getName(), filter);
		});
	}

	public IFieldOccurrenceFilter getFilter(String name) {
		return filters.get(name);
	}

	public void addFilter(IFieldOccurrenceFilter filter) {
		filters.put(filter.getName(), filter);
	}

	public void removeFilter(String name) {
		filters.remove(name);
	}

	/**
	 * This method applies the filters to the collection of FieldOccurrences
	 * @param occurrences
	 * @param params
	 * @return
	 */
	public Collection<FieldOccurrence> filter(Collection<FieldOccurrence> occurrences, String filterName, Map<String,String> params) {

		// if filter is defined, apply it
		if ( filters.containsKey(filterName) )
			return filters.get( filterName ).filter(occurrences, params);
		else // if no filter is defined, return the original collection
			return occurrences;
	}
}
