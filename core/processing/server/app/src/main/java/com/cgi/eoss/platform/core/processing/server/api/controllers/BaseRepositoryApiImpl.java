package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.PlatformEntityWithOwner;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Base implementation of the BaseRepositoryApi interface providing common repository operations.
 *
 * @param <T> the entity type managed by the repository
 */
public abstract class BaseRepositoryApiImpl<T extends PlatformEntityWithOwner<T>> implements BaseRepositoryApi<T> {

    @Override
    public <S extends T> S save(S entity) {

        return getDao().save(entity);
    }

    /**
     * Returns a paginated list of entities matching the provided predicate.
     *
     * @param predicate the filtering criteria
     * @param pageable  pagination information
     * @return a page containing the requested entities
     */
    protected Page<T> getFilteredResults(Predicate predicate, Pageable pageable) {

        return getDao().findAll(predicate, pageable);
    }

    protected abstract PlatformEntityDao<T> getDao();
}
