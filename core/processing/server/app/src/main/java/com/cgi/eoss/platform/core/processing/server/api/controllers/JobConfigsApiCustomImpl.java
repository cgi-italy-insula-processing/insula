package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.JobConfigDao;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Custom implementation of the {@link JobConfigsApiCustom} interface for managing {@link JobConfig} resources.
 */

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Getter
@Component
public class JobConfigsApiCustomImpl extends BaseRepositoryApiImpl<JobConfig> implements JobConfigsApiCustom {

    private final JobConfigDataService jobConfigDataService;
    private final JobConfigDao dao;
    private final UserDataService userDataService;

    @Override
    public <S extends JobConfig> S save(S entity) {

        entity.setOwner(userDataService.getDefaultUser());
        return (S) jobConfigDataService.save(entity);
    }
}
