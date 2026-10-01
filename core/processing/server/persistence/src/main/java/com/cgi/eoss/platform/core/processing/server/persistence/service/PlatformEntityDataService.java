package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformEntity;

/**
 * A directory to store, load and delete PlatformEntity objects. Provides data integrity and constraint checks
 * before passing to the DAO.
 *
 * @param <T> The data type to be provided.
 */
public interface PlatformEntityDataService<T extends PlatformEntity<T>> extends DataService<T, Long> {

}
