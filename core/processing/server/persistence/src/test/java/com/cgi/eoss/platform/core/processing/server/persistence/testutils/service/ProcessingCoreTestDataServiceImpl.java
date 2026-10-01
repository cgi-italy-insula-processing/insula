package com.cgi.eoss.platform.core.processing.server.persistence.testutils.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.JobConfigDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.JobDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformServiceContextFileDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformServiceDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserMountDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Service
@Transactional
public class ProcessingCoreTestDataServiceImpl implements ProcessingCoreTestDataService {

    @Autowired
    protected JobDao jobDao;
    @Autowired
    protected JobConfigDao jobConfigDao;
    @Autowired
    protected UserMountDao userMountDao;
    @Autowired
    private PlatformServiceDao platformServiceDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private PlatformServiceContextFileDao platformServiceContextFileDao;

    @Override
    public List<User> findAllUsers() {
        return userDao.findAll();
    }

    @Override
    public List<PlatformService> findAllPlatformServices() {
        return platformServiceDao.findAll();
    }

    @Override
    public List<PlatformServiceContextFile> findAllPlatformServiceContextFiles() {
        return platformServiceContextFileDao.findAll();
    }

    @Override
    public List<JobConfig> findAllJobConfigs() {
        return jobConfigDao.findAll();
    }

    @Override
    public List<Job> findAllJobs() {
        return jobDao.findAll();
    }

    @Override
    public List<UserMount> findAllUserMounts() {
        return userMountDao.findAll();
    }

    @Override
    public void deleteAllJobs() {
        jobDao.deleteAll();
    }

    @Override
    public void deleteAllJobConfigs() {
        jobConfigDao.deleteAll();
    }

    @Override
    public void deleteAllUsers() {
        userDao.deleteAll();
    }

    @Override
    public void deleteAllPlatformServices() {
        platformServiceDao.deleteAll();
    }

    @Override
    public void deleteAllPlatformServiceContextFiles() {
        platformServiceContextFileDao.deleteAll();
    }

    @Override
    public void deleteAllUserMounts() {
        userMountDao.deleteAll();
    }

    @Override
    public void dropAllJobConfigsRelations() {
        List<JobConfig> jobConfigs = jobConfigDao.findAll();
        for (JobConfig jobConfig : jobConfigs) {
            jobConfig.setParent(null);
            jobConfigDao.save(jobConfig);
        }
    }

    @Override
    public void dropAllJobsRelations() {
        List<Job> jobs = jobDao.findAll();
        for (Job job : jobs) {
            job.setParentJob(null);
            job.setSubJobs(null);
            jobDao.save(job);
        }
    }

    @Override
    public void resourcesCleanup() {

        dropAllJobConfigsRelations();
        dropAllJobsRelations();

        deleteAllJobs();
        deleteAllJobConfigs();

        deleteAllUserMounts();

        deleteAllPlatformServices();

        deleteAllUsers();
    }

    @Override
    public void assertDbIsEmpty() {
        assertThat(findAllPlatformServices().size(), is(0));
        assertThat(findAllJobConfigs().size(), is(0));
        assertThat(findAllJobs().size(), is(0));
        assertThat(findAllUserMounts().size(), is(0));
        assertThat(findAllUsers().size(), is(0));
    }

}
