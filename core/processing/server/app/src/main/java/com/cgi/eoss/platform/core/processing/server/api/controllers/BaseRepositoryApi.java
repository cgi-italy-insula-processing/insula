package com.cgi.eoss.platform.core.processing.server.api.controllers;

/**
 * Base contract for repository operations exposed by API controllers.
 *
 * @param <T> the entity type managed by the repository
 */
interface BaseRepositoryApi<T> {

    /**
     * Persists the provided entity instance.
     *
     * @param entity the entity to save
     * @param <S>    the concrete entity subtype
     * @return the persisted entity instance
     */
    <S extends T> S save(S entity);

}
