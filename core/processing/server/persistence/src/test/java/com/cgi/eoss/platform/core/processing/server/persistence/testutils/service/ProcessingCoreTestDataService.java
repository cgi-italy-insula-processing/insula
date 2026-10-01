package com.cgi.eoss.platform.core.processing.server.persistence.testutils.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;

import java.util.List;

public interface ProcessingCoreTestDataService {

    List<User> findAllUsers();

    List<PlatformService> findAllPlatformServices();

    List<PlatformServiceContextFile> findAllPlatformServiceContextFiles();

    List<JobConfig> findAllJobConfigs();

    List<Job> findAllJobs();

    List<UserMount> findAllUserMounts();

    void deleteAllUsers();

    void deleteAllPlatformServices();

    void deleteAllPlatformServiceContextFiles();

    void deleteAllJobConfigs();

    void deleteAllJobs();

    void deleteAllUserMounts();

    void dropAllJobsRelations();

    void dropAllJobConfigsRelations();

    void resourcesCleanup();

    void assertDbIsEmpty();
}
