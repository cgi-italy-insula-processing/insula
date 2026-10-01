package com.cgi.eoss.platform.core.processing.server.persistence.service;

import java.util.List;

import com.cgi.eoss.platform.core.processing.server.model.Searchable;

/**
 * <p>A data directory allowing simple string-based searching for entities.</p>
 */
@FunctionalInterface
public interface SearchableDataService<T extends Searchable> {

    /**
     * @return All entities matching the given search term across one or more fields. The set of searched fields depends
     * on the entity.
     */
    List<T> search(String term);

}
