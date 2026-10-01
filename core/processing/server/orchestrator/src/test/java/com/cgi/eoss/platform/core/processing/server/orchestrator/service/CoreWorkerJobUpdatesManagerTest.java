package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;

import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.protobuf.Timestamp;

public class CoreWorkerJobUpdatesManagerTest {

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final OutputProcessor outputProcessor = mock(OutputProcessor.class);

    private CoreWorkerJobUpdatesManager coreWorkerJobUpdatesManager;

    private InOrder inOrder;

    private User owner;
    private JobConfig jobConfig;

    @Before
    public void init() {
        coreWorkerJobUpdatesManager = new CoreWorkerJobUpdatesManager(jobDataService, outputProcessor);
        inOrder = inOrder(jobDataService, outputProcessor);
        owner = ProcessingCoreEntities.createUser().name("owner").build();
        jobConfig = new JobConfig(owner, ProcessingCoreEntities.createPlatformService(owner).name("serviceName").build());
    }

    @After
    public void shutdown() {
        verifyNoMoreInteractions(jobDataService, outputProcessor);
    }

    @Test
    public void testOnJobDataFetchingStarted_SavesJobInRunningStatusSettingStartTimeAndPhaseAndStageToDataFetching() {
        Job job = buildJob(JobStep.CREATED, Status.CREATED);
        job.setStartTime(null);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobDataFetchingStarted(job, "worker-1");
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getWorkerId()).isEqualTo("worker-1");
        assertThat(job.getStartTime()).isBetween(beforeCall, afterCall);
        assertThat(job.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(job.getPhase()).isEqualTo(JobStep.DATA_FETCH);
        assertThat(job.getStage()).isEqualTo(JobStep.DATA_FETCH.getText());

        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobDataFetchingStarted_DoesNotOverwriteStartTime_WhenAlreadySet() {
        Job job = buildJob(JobStep.CREATED, Status.CREATED);
        LocalDateTime originalStartTime = LocalDateTime.now().minusHours(1);
        job.setStartTime(originalStartTime);

        coreWorkerJobUpdatesManager.onJobDataFetchingStarted(job, "worker-1");

        assertThat(job.getStartTime()).isEqualTo(originalStartTime);
        assertThat(job.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(job.getPhase()).isEqualTo(JobStep.DATA_FETCH);

        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobDataFetchingCompleted_DoesNotPersistAnything() {
        Job job = buildJob(JobStep.DATA_FETCH, Status.RUNNING);

        coreWorkerJobUpdatesManager.onJobDataFetchingCompleted(job);
    }

    @Test
    public void testOnJobProcessingStarted_SavesJobInRunningStatusSettingPhaseAndStageToProcessing() {
        Job job = buildJob(JobStep.DATA_FETCH, Status.RUNNING);

        coreWorkerJobUpdatesManager.onJobProcessingStarted(job, Timestamp.getDefaultInstance(), "backend");

        assertThat(job.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(job.getPhase()).isEqualTo(JobStep.PROCESSING);
        assertThat(job.getStage()).isEqualTo(JobStep.PROCESSING.getText());

        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobProcessingStarted_DoesNothing_WhenAlreadyInProcessingPhase() {
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);

        coreWorkerJobUpdatesManager.onJobProcessingStarted(job, Timestamp.getDefaultInstance(), "backend");
    }

    @Test
    public void testOnJobProcessingCompleted_CompletesTheJobAndDelegatesOutputProcessing() throws Exception {
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getPhase()).isEqualTo(JobStep.OUTPUT_LIST);
        assertThat(job.getStage()).isEqualTo(JobStep.OUTPUT_LIST.getText());
        assertThat(job.getEndTime()).isBetween(beforeCall, afterCall);
        assertThat(job.getStatus()).isEqualTo(Status.COMPLETED);

        inOrder.verify(jobDataService).save(job);
        inOrder.verify(outputProcessor).processOutputs(job);
        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobProcessingCompleted_CompletesParentJob_WhenAllSubJobsAreCompleted() throws Exception {
        Job parentJob = buildJob(JobStep.PROCESSING, Status.RUNNING);
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);
        job.setParentJob(parentJob);
        Job refreshedParentJob = buildJob(JobStep.PROCESSING, Status.RUNNING);
        when(jobDataService.refreshFull(parentJob)).thenReturn(refreshedParentJob);
        when(jobDataService.allSubJobsCompleted(refreshedParentJob)).thenReturn(true);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(refreshedParentJob.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(refreshedParentJob.getPhase()).isEqualTo(JobStep.OUTPUT_LIST);
        assertThat(refreshedParentJob.getStage()).isEqualTo(JobStep.OUTPUT_LIST.getText());
        assertThat(refreshedParentJob.getEndTime()).isBetween(beforeCall, afterCall);

        inOrder.verify(jobDataService).save(job);
        inOrder.verify(outputProcessor).processOutputs(job);
        inOrder.verify(jobDataService).save(job);
        inOrder.verify(jobDataService).refreshFull(parentJob);
        inOrder.verify(jobDataService).allSubJobsCompleted(refreshedParentJob);
        inOrder.verify(jobDataService).save(refreshedParentJob);
    }

    @Test
    public void testOnJobProcessingCompleted_DoesNotCompleteParentJob_WhenNotAllSubJobsAreCompleted() throws Exception {
        Job parentJob = buildJob(JobStep.PROCESSING, Status.RUNNING);
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);
        job.setParentJob(parentJob);
        Job refreshedParentJob = buildJob(JobStep.PROCESSING, Status.RUNNING);
        when(jobDataService.refreshFull(parentJob)).thenReturn(refreshedParentJob);
        when(jobDataService.allSubJobsCompleted(refreshedParentJob)).thenReturn(false);

        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());

        assertThat(job.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(refreshedParentJob.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(refreshedParentJob.getPhase()).isEqualTo(JobStep.PROCESSING);
        assertThat(refreshedParentJob.getStage()).isEqualTo(JobStep.PROCESSING.getText());
        assertThat(refreshedParentJob.getEndTime()).isNull();

        inOrder.verify(jobDataService).save(job);
        inOrder.verify(outputProcessor).processOutputs(job);
        inOrder.verify(jobDataService).save(job);
        inOrder.verify(jobDataService).refreshFull(parentJob);
        inOrder.verify(jobDataService).allSubJobsCompleted(refreshedParentJob);
    }

    @Test
    public void testOnJobProcessingCompleted_DoesNothing_WhenAlreadyInOutputListPhase() {
        Job job = buildJob(JobStep.OUTPUT_LIST, Status.RUNNING);

        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
    }

    @Test
    public void testOnJobProcessingCompleted_EndsJobWithError_WhenInDataFetchingPhase() {
        Job job = buildJob(JobStep.DATA_FETCH, Status.RUNNING);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getStatus()).isEqualTo(Status.ERROR);
        assertThat(job.getEndTime()).isBetween(beforeCall, afterCall);

        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobProcessingCompleted_EndsJobWithError_WhenOutputProcessingThrows() throws Exception {
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);
        doThrow(new IOException("output processing failed")).when(outputProcessor).processOutputs(job);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getStatus()).isEqualTo(Status.ERROR);
        assertThat(job.getEndTime()).isBetween(beforeCall, afterCall);

        inOrder.verify(jobDataService).save(job);
        inOrder.verify(outputProcessor).processOutputs(job);
        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobHeartbeat_DoesNothing() {
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);

        coreWorkerJobUpdatesManager.onJobHeartbeat(job, Timestamp.getDefaultInstance());
    }

    @Test
    public void testOnJobError_IgnoresError_WhenJobAlreadyCompleted() {
        Job job = buildJob(JobStep.OUTPUT_LIST, Status.COMPLETED);

        coreWorkerJobUpdatesManager.onJobError(job, "some error");

        assertThat(job.getStatus()).isEqualTo(Status.COMPLETED);
    }

    @Test
    public void testOnJobError_EndsJobAndParentJobWithError_WhenJobHasParentJob() {
        Job parentJob = buildJob(JobStep.PROCESSING, Status.RUNNING);
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);
        job.setParentJob(parentJob);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobError(job, "some error");
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getStatus()).isEqualTo(Status.ERROR);
        assertThat(job.getEndTime()).isBetween(beforeCall, afterCall);
        assertThat(parentJob.getStatus()).isEqualTo(Status.ERROR);

        inOrder.verify(jobDataService).save(job);
        inOrder.verify(jobDataService).save(parentJob);
    }

    @Test
    public void testOnJobError_EndsJobWithError_WhenGivenThrowable() {
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobError(job, new IllegalStateException("exception"));
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getStatus()).isEqualTo(Status.ERROR);
        assertThat(job.getEndTime()).isBetween(beforeCall, afterCall);

        inOrder.verify(jobDataService).save(job);
    }

    @Test
    public void testOnJobError_EndsJobAndParentJobWithError_WhenGivenThrowableAndJobHasParentJob() {
        Job parentJob = buildJob(JobStep.PROCESSING, Status.RUNNING);
        Job job = buildJob(JobStep.PROCESSING, Status.RUNNING);
        job.setParentJob(parentJob);

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobError(job, new IllegalStateException("exception"));
        LocalDateTime afterCall = LocalDateTime.now();

        assertThat(job.getStatus()).isEqualTo(Status.ERROR);
        assertThat(job.getEndTime()).isBetween(beforeCall, afterCall);
        assertThat(parentJob.getStatus()).isEqualTo(Status.ERROR);

        inOrder.verify(jobDataService).save(job);
        inOrder.verify(jobDataService).save(parentJob);
    }

    private Job buildJob(JobStep phase, Status status) {
        Job job = ProcessingCoreEntities.createJob(owner, jobConfig).id(1L).extId("ext-id").build();
        job.setPhase(phase);
        job.setStage(phase.getText());
        job.setStatus(status);
        return job;
    }
}
