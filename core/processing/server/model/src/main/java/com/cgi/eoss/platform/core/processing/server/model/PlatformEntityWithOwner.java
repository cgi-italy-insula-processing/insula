package com.cgi.eoss.platform.core.processing.server.model;

public interface PlatformEntityWithOwner<T> extends PlatformEntity<T> {

    /**
     * @return The user who owns the entity.
     */
    User getOwner();

    /**
     * @param owner The new owner of the entity.
     */
    void setOwner(User owner);

}
