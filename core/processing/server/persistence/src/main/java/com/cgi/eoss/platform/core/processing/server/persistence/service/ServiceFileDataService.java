package com.cgi.eoss.platform.core.processing.server.persistence.service;

import java.util.List;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.persistence.service.PlatformEntityDataService;

public interface ServiceFileDataService extends
        PlatformEntityDataService<PlatformServiceContextFile> {
    List<PlatformServiceContextFile> findByService(PlatformService service);
}
