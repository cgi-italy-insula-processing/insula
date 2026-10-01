package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.CancelJobRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.StopJobRequest;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlatformJobStopperTest {

    private JobDataService jobDataService;
    private PlatformJobStopRequestSubmitter platformJobStopRequestSubmitter;
    private QueueService queueService;
    private final String pendingJobsQueueName = "pending-jobs-queue" ;
    private final String waitingJobsQueueName = "waiting-jobs-queue";

    private InOrder inOrder;

    private PlatformJobStopper platformJobStopper;

    @Before
    public void init() {
        jobDataService = mock(JobDataService.class);
        platformJobStopRequestSubmitter = mock(PlatformJobStopRequestSubmitter.class);
        queueService = mock(QueueService.class);

        inOrder = inOrder(jobDataService, platformJobStopRequestSubmitter, queueService);

        platformJobStopper = new PlatformJobStopper(jobDataService, platformJobStopRequestSubmitter, queueService,
                1, pendingJobsQueueName, waitingJobsQueueName);
    }

    @After
    public void shutDown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testStopJob_SubmitsJobStopRequest() {

        Long jobId = 57L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.RUNNING);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        platformJobStopper.stopJob(StopJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(platformJobStopRequestSubmitter).stopJob(job);
    }

    @Test
    public void testStopJob_ThrowsPlatformEntityNotFoundException_WhenRequestedJobDoesNotExist() {

        Long jobId = 71L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformJobStopper.stopJob(StopJobRequest.builder().intJobId(jobId.toString()).build()))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Failed to load Job with ID: " + jobId);

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testCancelJob_ThrowsPlatformEntityNotFoundException_WhenJobIsNotFound() {

        Long jobId = 84L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build()))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Failed to load Job with ID: " + jobId);

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testCancelJob_DoesNotCancelJob_WhenJobIsNotParentAndIsInInStatusCancelled() {

        Long jobId = 97L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.CANCELLED)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testCancelJob_DoesNotCancelJob_WhenJobIsNotParentAndIsInInStatusRunning() {

        Long jobId = 109L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.RUNNING)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testCancelJob_DoesNotCancelJob_WhenJobIsNotParentAndIsInInStatusCompleted() {

        Long jobId = 121L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.COMPLETED)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testCancelJob_DoesNotCancelJob_WhenJobIsNotParentAndIsInInStatusError() {

        Long jobId = 133L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.ERROR)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testCancelJob_PersistsJobInCancelledStatusAndDoesNotRemoveMessageFromQueues_WhenJobIsNotParentAndIsInStatusCreated() {

        Long jobId = 145L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.CREATED)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(jobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PersistsJobInCancelledStatusAndDoesNotRemoveMessageFromQueues_WhenJobIsNotParentAndIsInStatusConditionWait() {

        Long jobId = 163L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.CONDITION_WAIT)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(jobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PersistsJobInCancelledStatusAndRemovesMessageFromPendingJobsQueue_WhenJobIsNotParentAndIsInStatusPending() {

        Long jobId = 181L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(queueService).receiveSelectedObjectNoWait(pendingJobsQueueName, "jobId = '" + jobId + "'");

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(jobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PersistsJobInCancelledStatusAndRemovesMessageFromWaitingJobsQueue_WhenJobIsNotParentAndIsInStatusWaiting() {

        Long jobId = 200L;
        when(jobDataService.getById(jobId))
                .thenReturn(Optional.of(createJobWithParentFalseAndStatus(jobId, Job.Status.WAITING)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(queueService).receiveSelectedObjectNoWait(waitingJobsQueueName, "jobId = '" + jobId + "'");

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(jobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_DoesNotCancelJobs_WhenJobIsParentAndNoSubJobsIdsAreFound() {

        Long jobId = 219L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));
        when(jobDataService.getSubJobIds(job)).thenReturn(Collections.emptyList());

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
    }

    @Test
    public void testCancelJob_DoesNotCancelJobs_WhenJobIsParentAndNoSubJobsAreFound() {

        Long jobId = 235L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        ImmutableList<Long> subJobsIds = ImmutableList.of(241L);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds)).thenReturn(Collections.emptyList());

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
    }

    @Test
    public void testCancelJob_DoesNotCancelJobs_WhenJobIsParentAndSubJobsAreInStatusCancelled() {

        Long jobId = 256L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 262L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.CANCELLED)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
    }

    @Test
    public void testCancelJob_DoesNotCancelJobs_WhenJobIsParentAndSubJobsAreInStatusRunning() {

        Long jobId = 279L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 285L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.RUNNING)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
    }

    @Test
    public void testCancelJob_DoesNotCancelJobs_WhenJobIsParentAndSubJobsAreInStatusCompleted() {

        Long jobId = 302L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 308L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.COMPLETED)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
    }

    @Test
    public void testCancelJob_DoesNotCancelJobs_WhenJobIsParentAndSubJobsAreInStatusError() {

        Long jobId = 325L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 331L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.ERROR)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
    }

    @Test
    public void testCancelJob_PersistsSubJobsInStatusCancelledAndRemovesMessagesFromPendingJobsQueue_WhenJobIsParentAndSubJobsAreInStatusPending() {

        Long jobId = 348L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 354L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.PENDING)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
        inOrder.verify(queueService).receiveSelectedObjectNoWait(pendingJobsQueueName, "jobId = '" + subJobId + "'");

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(subJobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PersistsSubJobsInStatusCancelledAndRemovesMessagesFromPendingJobsQueue_WhenJobIsParentAndSubJobsAreInStatusWaiting() {

        Long jobId = 378L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 384L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.WAITING)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);
        inOrder.verify(queueService).receiveSelectedObjectNoWait(waitingJobsQueueName, "jobId = '" + subJobId + "'");

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(subJobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PersistsSubJobsInStatusCancelledAndRemovesMessagesFromPendingJobsQueue_WhenJobIsParentAndSubJobsAreInStatusConditionWait() {

        Long jobId = 408L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 414L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.CONDITION_WAIT)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(subJobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PersistsSubJobsInStatusCancelledAndRemovesMessagesFromPendingJobsQueue_WhenJobIsParentAndSubJobsAreInStatusCreated() {

        Long jobId = 437L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobId = 443L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobId);
        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        when(jobDataService.findByIds(subJobsIds))
                .thenReturn(ImmutableList.of(createJobWithParentFalseAndStatus(subJobId, Job.Status.CREATED)));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);
        inOrder.verify(jobDataService).findByIds(subJobsIds);

        ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
        inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
        Job savedJob = jobSaveCaptor.getValue();
        assertThat(savedJob.getId()).isEqualTo(subJobId);
        assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testCancelJob_PaginatesSubJobsRetrievalWhenCancellingSubJobs_WhenJobIsParentAndSubJobsNumberIsGreaterThanSubJobsPageSize() {

        Long jobId = 466L;
        Job job = createJobWithParentFalseAndStatus(jobId, Job.Status.PENDING);
        job.setParent(true);

        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        long subJobOneId = 472L;
        long subJobTwoId = 473L;
        ImmutableList<Long> subJobsIds = ImmutableList.of(subJobOneId, subJobTwoId);

        when(jobDataService.getSubJobIds(job)).thenReturn(subJobsIds);

        Job subJobOne = createJobWithParentFalseAndStatus(subJobOneId, Job.Status.PENDING);
        when(jobDataService.findByIds(ImmutableList.of(subJobOneId))).thenReturn(ImmutableList.of(subJobOne));

        Job subJobTwo = createJobWithParentFalseAndStatus(subJobTwoId, Job.Status.PENDING);
        when(jobDataService.findByIds(ImmutableList.of(subJobTwoId))).thenReturn(ImmutableList.of(subJobTwo));

        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(jobId.toString()).build());

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(job);

        {
            inOrder.verify(jobDataService).findByIds(ImmutableList.of(subJobOneId));
            inOrder.verify(queueService).receiveSelectedObjectNoWait(pendingJobsQueueName, "jobId = '" + subJobOneId + "'");
            ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
            inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
            Job savedJob = jobSaveCaptor.getValue();
            assertThat(savedJob.getId()).isEqualTo(subJobOneId);
            assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
        }
        {
            inOrder.verify(jobDataService).findByIds(ImmutableList.of(subJobTwoId));
            inOrder.verify(queueService).receiveSelectedObjectNoWait(pendingJobsQueueName, "jobId = '" + subJobTwoId + "'");
            ArgumentCaptor<Job> jobSaveCaptor = ArgumentCaptor.forClass(Job.class);
            inOrder.verify(jobDataService).save(jobSaveCaptor.capture());
            Job savedJob = jobSaveCaptor.getValue();
            assertThat(savedJob.getId()).isEqualTo(subJobTwoId);
            assertThat(savedJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
        }
    }

    private Job createJobWithParentFalseAndStatus(Long jobId, Job.Status jobStatus) {
        Job job = new Job();
        job.setId(jobId);
        job.setStatus(jobStatus);
        return job;
    }
}