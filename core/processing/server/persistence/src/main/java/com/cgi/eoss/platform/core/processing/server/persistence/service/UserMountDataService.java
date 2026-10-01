package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.UserMount;

import java.util.Optional;

/**
 * Service interface for managing {@link UserMount} entities.
 */
public interface UserMountDataService extends PlatformEntityDataService<UserMount> {

    /**
     * Retrieves a {@link UserMount} entity by its name.
     *
     * @param name the name of the {@code UserMount} to retrieve
     * @return an {@link Optional} containing the {@code UserMount} if found, or empty if not
     */
    Optional<UserMount> getByName(String name);
}
