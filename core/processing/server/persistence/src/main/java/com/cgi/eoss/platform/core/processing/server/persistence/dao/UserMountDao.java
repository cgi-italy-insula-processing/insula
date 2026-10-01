package com.cgi.eoss.platform.core.processing.server.persistence.dao;

import com.cgi.eoss.platform.core.processing.server.model.UserMount;

import java.util.Optional;

public interface UserMountDao extends PlatformEntityDao<UserMount> {

    Optional<UserMount> findByName(String name);

}
