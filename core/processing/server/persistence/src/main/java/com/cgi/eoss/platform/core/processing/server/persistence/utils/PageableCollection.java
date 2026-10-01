package com.cgi.eoss.platform.core.processing.server.persistence.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A utility class that provides pagination functionality for a collection of items.
 *
 */
public class PageableCollection<K> {

    private final List<K> items;
    private final int pageSize;
    private int currentPage;

    /**
     * Constructs a PageableCollection with the given set of items and page size.
     *
     * @param values   the set of items to be paginated; must not be {@code null}
     * @param pageSize the number of items per page; must be greater than 0
     * @throws IllegalArgumentException if pageSize is not greater than 0
     */
    public PageableCollection(Collection<K> values, int pageSize) {

        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }

        this.items = Collections.unmodifiableList(new ArrayList<>(values));
        this.pageSize = pageSize;
        this.currentPage = 0;
    }

    /**
     * Returns the next page of items and advances the internal position.
     *
     * @return the next page, or null if no more pages are available
     */
    public List<K> next() {
        if (!hasNext()) {
            return null;
        }

        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, items.size());

        List<K> page = items.subList(startIndex, endIndex);
        currentPage++;

        return page;
    }

    /**
     * Checks if there are more pages available for sequential iteration.
     *
     * @return true if more pages exist, false otherwise
     */
    public boolean hasNext() {
        return currentPage * pageSize < items.size();
    }

}
