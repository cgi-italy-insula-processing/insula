package com.cgi.eoss.platform.core.processing.server.persistence.service;

import java.util.List;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.PlatformEntityDataService;

public interface JobConfigDataService extends
        PlatformEntityDataService<JobConfig> {

    List<JobConfig> findByOwner(User user);

    List<JobConfig> findByService(PlatformService service);

    List<JobConfig> findByOwnerAndService(User user, PlatformService service);

}
