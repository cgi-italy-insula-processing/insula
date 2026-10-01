package com.cgi.eoss.platform.core.processing.server.persistence.dao;

import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;

import java.util.List;

public interface UserDao extends PlatformEntityDao<User> {
    User findOneByName(String name);
    List<User> findByNameContainingIgnoreCase(String term);
}
