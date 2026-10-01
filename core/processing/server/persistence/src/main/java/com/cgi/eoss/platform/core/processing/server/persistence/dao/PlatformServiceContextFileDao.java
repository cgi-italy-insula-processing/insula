package com.cgi.eoss.platform.core.processing.server.persistence.dao;

import java.util.List;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;

public interface PlatformServiceContextFileDao extends PlatformEntityDao<PlatformServiceContextFile> {
    List<PlatformServiceContextFile> findByService(PlatformService service);
}
