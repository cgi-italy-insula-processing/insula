package com.cgi.eoss.platform.core.processing.server.persistence.service;

import java.util.List;
import java.util.Optional;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.PlatformEntityDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.SearchableDataService;

public interface ServiceDataService extends
        PlatformEntityDataService<PlatformService>,
        SearchableDataService<PlatformService> {
    List<PlatformService> findByOwner(User user);

    Optional<PlatformService> getByName(String serviceName);

    List<PlatformService> findAllAvailable();

    String computeServiceFingerprint(PlatformService platformService);

	String getPlatformDockerPrefix();

}
