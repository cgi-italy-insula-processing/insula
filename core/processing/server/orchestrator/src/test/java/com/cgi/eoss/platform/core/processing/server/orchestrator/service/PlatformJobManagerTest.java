package com.cgi.eoss.platform.core.processing.server.orchestrator.service;


import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchRequest;

import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchResponse;
import org.junit.After;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class PlatformJobManagerTest {

    private static final String SERVICE_NAME = "my-service";
    private static final String USER_NAME = "user-name";

    private final PlatformJobLauncherCore platformJobLauncherCore = Mockito.mock(PlatformJobLauncherCore.class);
    private final PlatformJobSubmitter platformJobSubmitter = Mockito.mock(PlatformJobSubmitter.class);
    private final InOrder inOrder = Mockito.inOrder(platformJobLauncherCore, platformJobSubmitter);

    private final PlatformJobManager platformJobManager = new PlatformJobManager(platformJobLauncherCore, platformJobSubmitter);

    @After
    public void shutdown() {

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLaunchJob_SubmitsTheJobLaunchRequest_WhenServiceIsProcessor() {

        JobLaunchRequest jobLaunchRequest = buildJobLaunchRequest();
        User owner = buildUser("user-uuid");
        Job job = buildJob(1L, "job-ext-id", owner, PlatformService.Type.PROCESSOR, null);

        JobSubmissionRequest jobSubmissionRequest = JobSubmissionRequest.builder().job(job).build();
        List<JobSubmissionRequest> jobSubmissionRequests = new ArrayList<>();
        jobSubmissionRequests.add(jobSubmissionRequest);
        when(platformJobLauncherCore.submitJob(jobLaunchRequest)).thenReturn(jobSubmissionRequests);

        JobLaunchResponse jobLaunchResponse = platformJobManager.launchJob(jobLaunchRequest);

        // The owner has a uuid, so it overrides the user name in the response
        assertThat(jobLaunchResponse).isEqualTo(JobLaunchResponse.builder()
            .jobId("job-ext-id")
            .intJobId("1")
            .userId("user-name")
            .serviceId(SERVICE_NAME)
            .build());

        inOrder.verify(platformJobLauncherCore).submitJob(jobLaunchRequest);
        inOrder.verify(platformJobSubmitter).submitJob(jobSubmissionRequest);
    }

    @Test
    public void testLaunchJob_SubmitsTheJobLaunchRequest_WhenServiceIsParallelProcessor() {

        JobLaunchRequest jobLaunchRequest = buildJobLaunchRequest();
        User owner = buildUser("user-uuid");
        Job parentJob = buildJob(1L, "parent-job-ext-id", owner, PlatformService.Type.PARALLEL_PROCESSOR, null);
        Job firstSubJob = buildJob(2L, "sub-job-1-ext-id", owner, PlatformService.Type.PARALLEL_PROCESSOR, parentJob);
        Job secondSubJob = buildJob(3L, "sub-job-2-ext-id", owner, PlatformService.Type.PARALLEL_PROCESSOR, parentJob);

        JobSubmissionRequest firstSubJobRequest = JobSubmissionRequest.builder().job(firstSubJob).build();
        JobSubmissionRequest secondSubJobRequest = JobSubmissionRequest.builder().job(secondSubJob).build();
        List<JobSubmissionRequest> jobSubmissionRequests = Arrays.asList(firstSubJobRequest, secondSubJobRequest);
        when(platformJobLauncherCore.submitJob(jobLaunchRequest)).thenReturn(jobSubmissionRequests);

        JobLaunchResponse jobLaunchResponse = platformJobManager.launchJob(jobLaunchRequest);

        // Every sub job is submitted, but the response describes the parent job
        assertThat(jobLaunchResponse).isEqualTo(JobLaunchResponse.builder()
            .jobId("parent-job-ext-id")
            .intJobId("1")
            .userId(USER_NAME)
            .serviceId(SERVICE_NAME)
            .build());

        inOrder.verify(platformJobLauncherCore).submitJob(jobLaunchRequest);
        inOrder.verify(platformJobSubmitter).submitJob(firstSubJobRequest);
        inOrder.verify(platformJobSubmitter).submitJob(secondSubJobRequest);
    }

    @Test
    public void testLaunchJob_ThrowsException_WhenJobSubmitterFails() {

        JobLaunchRequest jobLaunchRequest = buildJobLaunchRequest();
        User owner = buildUser("user-uuid");
        Job job = buildJob(1L, "job-ext-id", owner, PlatformService.Type.PROCESSOR, null);

        JobSubmissionRequest jobSubmissionRequest = JobSubmissionRequest.builder().job(job).build();
        List<JobSubmissionRequest> jobSubmissionRequests = new ArrayList<>();
        jobSubmissionRequests.add(jobSubmissionRequest);
        when(platformJobLauncherCore.submitJob(jobLaunchRequest)).thenReturn(jobSubmissionRequests);
        doThrow(new IllegalStateException("Error"))
            .when(platformJobSubmitter).submitJob(jobSubmissionRequest);

        assertThatThrownBy(() -> platformJobManager.launchJob(jobLaunchRequest))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Error");

        inOrder.verify(platformJobLauncherCore).submitJob(jobLaunchRequest);
        inOrder.verify(platformJobSubmitter).submitJob(jobSubmissionRequest);
    }

    private static JobLaunchRequest buildJobLaunchRequest() {
        return JobLaunchRequest.builder()
            .jobId("job-ext-id")
            .userId(USER_NAME)
            .serviceId(SERVICE_NAME)
            .build();
    }

    private static User buildUser(String uuid) {
        User user = new User();
        user.setName(USER_NAME);
        user.setUuid(uuid);
        return user;
    }

    private static Job buildJob(Long id, String extId, User owner, PlatformService.Type serviceType, Job parentJob) {
        PlatformService service = new PlatformService(SERVICE_NAME, owner, "docker-tag");
        service.setType(serviceType);

        Job job = new Job(new JobConfig(owner, service), extId, owner, parentJob);
        job.setId(id);
        return job;
    }

}
