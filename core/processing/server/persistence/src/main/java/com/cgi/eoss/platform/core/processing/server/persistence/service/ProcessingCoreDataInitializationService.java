package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.User;

/**
 * Defines a service able to retrieve and expose default platform entities and properties
 *
 */
public interface ProcessingCoreDataInitializationService {

    /**
     * Retrieve the default user entity
     *
     * @return
     *         The default user entity
     */
    User getDefaultUser();

    /**
     * Retrieve the default user name
     *
     * @return
     *         The default user name
     */
    String getDefaultUserName();

    /**
     * Retrieve the default admin entity
     *
     * @return
     *         The default admin entity
     */
    User getDefaultAdmin();

    /**
     * Get the platform docker image prefix
     *
     * @return
     *         The platform docker image prefix
     */
    String getPlatformDockerPrefix();

    /**
     * Checks whether default entities exist and, if this is not the case, creates them
     *
     */
    void ensureDefaultEntitiesExist();
}
