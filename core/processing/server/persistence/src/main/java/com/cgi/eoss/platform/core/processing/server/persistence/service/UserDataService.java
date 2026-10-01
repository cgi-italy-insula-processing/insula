package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.PlatformEntityDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.SearchableDataService;

/**
 * Data Service for CRUD operations on User entities
 *
 */
public interface UserDataService extends PlatformEntityDataService<User>, SearchableDataService<User> {

    /**
     * Retrieve a persisted User with the given name
     *
     * @param name
     *            The name of the User to retrieve
     *
     * @return
     *         The User having the provided name if found, or null otherwise
     */
    User getByName(String name);

    /**
     * <p>
     * Return a persisted user with the given name, creating a new entity if none already exists.
     * </p>
     * <p>
     * This method is intended for use where automatic user creation is necessary.
     * </p>
     *
     * @param name The desired username.
     * @return A persisted user entity with the given name.
     */
    User getOrSave(String name);

    /**
     * Retrieve the platform default User entity
     *
     * @return
     *         The platform default User entity
     */
    User getDefaultUser();

    /**
     * Get the name of the platform default User
     *
     * @return
     *         The name of the platform default User
     */
    String getDefaultUserName();

    /**
     * Retrieve the platform default Admin-User entity
     *
     * @return
     *         The platform default Admin-User entity
     */
    User getDefaultAdmin();
}
