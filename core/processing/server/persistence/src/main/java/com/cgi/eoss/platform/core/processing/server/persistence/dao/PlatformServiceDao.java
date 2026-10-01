package com.cgi.eoss.platform.core.processing.server.persistence.dao;

import java.util.List;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;

public interface PlatformServiceDao extends PlatformEntityDao<PlatformService> {
    List<PlatformService> findByNameContainingIgnoreCase(String term);

    List<PlatformService> findByOwner(User user);

    List<PlatformService> findByStatus(PlatformService.Status status);
}
