package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.CancelJobRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.StopJobRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.StopJob;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class PlatformJobStopperIT {

    @Autowired
    private PlatformJobStopper platformJobStopper;

    @Autowired
    private JobDataService jobDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Value("${platform.orchestrator.job.stopper.pendingQueueName:platform-pending-jobs}")
    private String pendingJobsQueueName;

    @Value("${platform.orchestrator.job.stopper.waitingQueueName:platform-jobs}")
    private String waitingJobsQueueName;

    private User owner;

    @Before
    public void init() {

        initQueues();

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        owner = userDataService.save(ProcessingCoreEntities.createUser().name("job-owner").build());
    }

    @After
    public void shutdown() {

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(0);
        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(0);
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(0);
    }

    @Test
    public void testStopJob_EnqueuesJobStopMessage() {

        Job job = createBaseJobToPersist();
        job.setStatus(Job.Status.RUNNING);
        job = jobDataService.save(job);

        platformJobStopper.stopJob(StopJobRequest.builder().intJobId(job.getId().toString()).build());

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(1);

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_STOP_REQUESTS);

        Map<String, Object> headers = message.getHeaders();
        assertThat(headers).hasSize(2);
        assertThat(headers.get("workerId")).isEqualTo(job.getWorkerId());
        assertThat(headers.get("jobId")).isEqualTo(job.getId().toString());

        StopJob payload = (StopJob) message.getPayload();

        assertThat(payload.getJob().getId()).isEqualTo(job.getExtId());
        assertThat(payload.getJob().getIntJobId()).isEqualTo(job.getId().toString());
        assertThat(payload.getJob().getUserId()).isEqualTo(owner.getName());
        assertThat(payload.getJob().getServiceId()).isEqualTo(job.getConfig().getService().getName());

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(0);

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job persistedJob = persistedJobs.get(0);
        assertThat(persistedJob.getId()).isEqualTo(job.getId());
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.RUNNING);
    }

    @Test
    public void testCancelJob_PersistsJobInStatusCancelledAndDequeuesMessageFromPendingJobsQueue_WhenJobIsNotParentAndIsInStatusPending() {

        Job pendingJob = createBaseJobToPersist();
        pendingJob.setStatus(Job.Status.PENDING);
        pendingJob.setParent(false);
        pendingJob = jobDataService.save(pendingJob);

        enqueueMessageForJob(pendingJob, pendingJobsQueueName);

        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(1);

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(pendingJob.getId().toString()).build());

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job canceledJob = persistedJobs.get(0);
        assertThat(canceledJob.getId()).isEqualTo(pendingJob.getId());
        assertThat(canceledJob.getConfig()).isEqualTo(pendingJob.getConfig());
        assertThat(canceledJob.getOwner()).isEqualTo(owner);
        assertThat(canceledJob.isParent()).isFalse();
        assertThat(canceledJob.getStatus()).isEqualTo(Job.Status.CANCELLED);

        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(0);
    }

    @Test
    public void testCancelJob_PersistsJobInStatusCancelledAndDequeuesMessageFromWaitingJobsQueue_WhenJobIsNotParentAndIsInStatusWaiting() {

        Job pendingJob = createBaseJobToPersist();
        pendingJob.setStatus(Job.Status.WAITING);
        pendingJob.setParent(false);
        pendingJob = jobDataService.save(pendingJob);

        enqueueMessageForJob(pendingJob, waitingJobsQueueName);

        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(1);

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(pendingJob.getId().toString()).build());

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job canceledJob = persistedJobs.get(0);
        assertThat(canceledJob.getId()).isEqualTo(pendingJob.getId());
        assertThat(canceledJob.getConfig()).isEqualTo(pendingJob.getConfig());
        assertThat(canceledJob.getOwner()).isEqualTo(owner);
        assertThat(canceledJob.isParent()).isFalse();
        assertThat(canceledJob.getStatus()).isEqualTo(Job.Status.CANCELLED);

        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(0);
    }

    @Test
    public void testCancelJob_PersistsSubJobsInStatusCancelledAndDequeuesMessageFromJobsQueues_WhenJobIsParentAndHasStartedSubJobs() {

        Job parentJob = createBaseJobToPersist();
        parentJob.setStatus(Job.Status.CREATED);
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job pendingSubJob = createBaseJobToPersist();
        {
            pendingSubJob.setStatus(Job.Status.PENDING);
            pendingSubJob.setParent(false);
            pendingSubJob.setParentJob(parentJob);
            pendingSubJob = jobDataService.save(pendingSubJob);

            enqueueMessageForJob(pendingSubJob, pendingJobsQueueName);
            assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(1);
        }

        Job waitingSubJob = createBaseJobToPersist();
        {
            waitingSubJob.setStatus(Job.Status.WAITING);
            waitingSubJob.setParent(false);
            waitingSubJob.setParentJob(parentJob);
            waitingSubJob = jobDataService.save(waitingSubJob);

            enqueueMessageForJob(waitingSubJob, waitingJobsQueueName);
            assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(1);
        }

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(parentJob.getId().toString()).build());

        assertThat(jobDataService.getAll())
                .hasSize(3);
        assertThat(jobDataService.findByIds(ImmutableList.of(parentJob.getId(), pendingSubJob.getId(), waitingSubJob.getId())))
                .hasSize(3);

        {
            Job persistedParentJob = jobDataService.getById(parentJob.getId()).get();
            assertThat(persistedParentJob.getConfig()).isEqualTo(parentJob.getConfig());
            assertThat(persistedParentJob.getOwner()).isEqualTo(owner);
            assertThat(persistedParentJob.isParent()).isTrue();
            assertThat(persistedParentJob.getStatus()).isEqualTo(Job.Status.CREATED);

            Job cancelledPendingSubJob = jobDataService.getById(pendingSubJob.getId()).get();
            assertThat(cancelledPendingSubJob.getConfig()).isEqualTo(pendingSubJob.getConfig());
            assertThat(cancelledPendingSubJob.getOwner()).isEqualTo(owner);
            assertThat(cancelledPendingSubJob.isParent()).isFalse();
            assertThat(cancelledPendingSubJob.getParentJob()).isEqualTo(persistedParentJob);
            assertThat(cancelledPendingSubJob.getStatus()).isEqualTo(Job.Status.CANCELLED);

            Job cancelledWaitingSubJob = jobDataService.getById(waitingSubJob.getId()).get();
            assertThat(cancelledWaitingSubJob.getConfig()).isEqualTo(waitingSubJob.getConfig());
            assertThat(cancelledWaitingSubJob.getOwner()).isEqualTo(owner);
            assertThat(cancelledWaitingSubJob.isParent()).isFalse();
            assertThat(cancelledWaitingSubJob.getParentJob()).isEqualTo(persistedParentJob);
            assertThat(cancelledWaitingSubJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
        }

        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(0);
        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(0);
    }

    private void enqueueMessageForJob(Job job, String queueName) {

        HashMap<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("jobId", String.valueOf(job.getId()));
        queueService.sendObject(queueName, messageHeaders, buildJobSpecForJob(job), 1);
    }

    private static JobSpec buildJobSpecForJob(Job job) {

        JobSpec.Builder jobSpecBuilder = JobSpecMapper.buildJobSpecBuilder(
                JobSubmissionRequest.builder()
                        .job(job)
                        .jobInputs(JobInputs.builder().inputs(new HashMap<>()).build())
                        .jobResourceRequirement(new JobResourceRequirement())
                        .build(),
                Collections.emptyList());
        jobSpecBuilder.setKind(Kind.WORKFLOW);
        return jobSpecBuilder.build();
    }

    private Job createBaseJobToPersist() {

        Job job = ProcessingCoreEntities.createJob(
                owner,
                jobConfigDataService.save(new JobConfig(
                        owner,
                        serviceDataService.save(
                                ProcessingCoreEntities.createPlatformService(owner).build()))))
                .build();

        job.setWorkerId("workerId");
        return job;
    }

    private void initQueues() {

        initQueue(ProcessingCoreQueueNames.JOB_STOP_REQUESTS);
        initQueue(pendingJobsQueueName);
        initQueue(waitingJobsQueueName);
    }

    private void initQueue(String queueName) {

        queueService.sendObject(queueName, "{}");
        queueService.receiveObjectNoWait(queueName);
        assertThat(queueService.getQueueLength(queueName)).isEqualTo(0);
    }

}