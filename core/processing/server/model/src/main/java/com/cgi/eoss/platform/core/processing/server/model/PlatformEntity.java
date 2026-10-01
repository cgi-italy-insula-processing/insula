package com.cgi.eoss.platform.core.processing.server.model;

import com.cgi.eoss.platform.core.processing.server.model.utils.Identifiable;

public interface PlatformEntity<T> extends Comparable<T>, Identifiable<Long>{

    /**
     * @return The unique identifier of the entity.
     */
    Long getId();

    /**
     * @param id The unique identifier of the entity.
     */
    void setId(Long id);

}
