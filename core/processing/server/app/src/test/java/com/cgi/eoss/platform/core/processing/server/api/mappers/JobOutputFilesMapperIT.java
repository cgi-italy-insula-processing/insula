package com.cgi.eoss.platform.core.processing.server.api.mappers;

import com.cgi.eoss.platform.core.processing.server.api.ApiConfig;
import com.cgi.eoss.platform.core.processing.server.api.ApiTestConfig;
import com.cgi.eoss.platform.core.processing.server.api.resources.JobOutputFileResource;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.Link;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {ApiConfig.class, ApiTestConfig.class})
@RunWith(SpringRunner.class)
@TestPropertySource("classpath:test-application.properties")
public class JobOutputFilesMapperIT {

    @Autowired
    private JobOutputFilesMapper jobOutputFilesMapper;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Autowired
    private JobDataService jobDataService;

    private User platformUser;

    @Before
    public void init() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("insula-test");
        request.setServerPort(8443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        platformUser = userDataService.save(ProcessingCoreEntities.createUser().build());
    }

    @After
    public void shutdown() {
        RequestContextHolder.resetRequestAttributes();

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testToJobOutputFileResources_MapsTheOutputFilesOfTheSubJobsLinkedToTheSubJobThatProducedThem_WhenJobIsParent() {

        Job parentJob = persistParentJob("parentJobExtId");
        Job subJobA = persistSubJob("subJobAExtId", parentJob, ImmutableListMultimap.of("outputId", "subJobAExtId/outputId/a.tif"));
        jobDataService.updateParentJobOutputs(subJobA);
        Job subJobB = persistSubJob("subJobBExtId", parentJob, ImmutableListMultimap.of("outputId", "subJobBExtId/outputId/b.tif"));
        parentJob = jobDataService.updateParentJobOutputs(subJobB);

        List<JobOutputFileResource> jobOutputFileResources = jobOutputFilesMapper.toJobOutputFileResources(parentJob);

        assertThat(jobOutputFileResources).containsExactlyInAnyOrder(
                new JobOutputFileResource("a.tif").add(Link.of(
                        "http://insula-test:8443/api/jobs/" + subJobA.getId() + "/outputs/outputId?filename=a.tif", "download")),
                new JobOutputFileResource("b.tif").add(Link.of(
                        "http://insula-test:8443/api/jobs/" + subJobB.getId() + "/outputs/outputId?filename=b.tif", "download")));
    }

    private Job persistParentJob(String jobExtId) {
        PlatformService service = serviceDataService.save(ProcessingCoreEntities.createPlatformService(platformUser).build());
        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(platformUser, service));
        Job parentJob = ProcessingCoreEntities.createJob(platformUser, jobConfig).extId(jobExtId).build();
        parentJob.setParent(true);
        return jobDataService.save(parentJob);
    }

    private Job persistSubJob(String jobExtId, Job parentJob, Multimap<String, String> outputs) {
        return jobDataService.save(ProcessingCoreEntities.createJob(platformUser, parentJob.getConfig())
                .extId(jobExtId)
                .parentJob(parentJob)
                .outputs(outputs)
                .build());
    }

}
