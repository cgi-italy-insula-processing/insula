package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.worker.JobError;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service.KubernetesWorkerJobDataService;

import io.kubernetes.client.openapi.ApiException;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KubernetesWorkerJobUpdatesManagerImplTest {

    private static final String JOB_ID = "theJobId";
    private static final String INT_JOB_ID = "10";

    private static final JobSpec WORKFLOW_JOB_SPEC = JobSpec.newBuilder()
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                    .setId(JOB_ID)
                    .setIntJobId(INT_JOB_ID)
                    .build())
            .build();

    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final JobUpdateListener jobUpdateListener = mock(JobUpdateListener.class);
    private final KubernetesWorkerJobDataService kubernetesWorkerJobDataService = mock(KubernetesWorkerJobDataService.class);
    private final InOrder inOrder = inOrder(workflowService, jobUpdateListener, kubernetesWorkerJobDataService);

    private final boolean deleteOnFailure = false;
    private KubernetesWorkerJobUpdatesManagerImpl kubernetesWorkerJobUpdatesManager = new KubernetesWorkerJobUpdatesManagerImpl(
            workflowService, jobUpdateListener, kubernetesWorkerJobDataService, deleteOnFailure);

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnWorkflowStartRequested_CreatesK8sWorkerJobWithStartingStatusAndForwardsDataFetchingStartedEvent() throws Exception {

        OffsetDateTime testStartTime = OffsetDateTime.now();

        kubernetesWorkerJobUpdatesManager.onWorkflowStartRequested(new KubernetesWorkerJob(JOB_ID, INT_JOB_ID), WORKFLOW_JOB_SPEC);

        inOrder.verify(kubernetesWorkerJobDataService).findByJobId(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTING);
        assertThat(savedKubernetesWorkerJob.getStart()).isBetween(testStartTime, OffsetDateTime.now());
        assertThat(savedKubernetesWorkerJob.getEnd()).isNull();

        inOrder.verify(workflowService)
                .createLegacyWorkflow(WORKFLOW_JOB_SPEC);

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.DATA_FETCHING_STARTED);
    }

    @Test
    public void testOnWorkflowStopRequested_CreatesK8sWorkerJobWithStoppedStatusAndForwardsProcessingCompleted_WhenK8sWorkerJobDoesNotExist() throws Exception {

        OffsetDateTime testStartTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobToStop = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
        when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobToStop)).thenReturn(kubernetesWorkerJobToStop);

        kubernetesWorkerJobUpdatesManager.onWorkflowStopRequested(kubernetesWorkerJobToStop);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobToStop);

        inOrder.verify(workflowService)
                .deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STOPPED);
        assertThat(savedKubernetesWorkerJob.getStart()).isNull();
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(testStartTime, OffsetDateTime.now());

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_COMPLETED);
    }

    @Test
    public void testOnWorkflowStopRequested_DoesNotUpdateK8sWorkerJobStatus_WhenK8sWorkerJobIsAlreadyCompleted() throws Exception {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobToStop = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.COMPLETED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobToStop)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowStopRequested(kubernetesWorkerJobToStop);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobToStop);

    }

    @Test
    public void testOnWorkflowStopRequested_UpdatesK8sWorkerJobStatusToErrorAndForwardsErrorEvent_WhenDeleteWorkflowFails() throws Exception {

        OffsetDateTime testStartTime = OffsetDateTime.now();
        OffsetDateTime jobStartTime = testStartTime.plusSeconds(1L);

        KubernetesWorkerJob kubernetesWorkerJobToStop = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStart(jobStartTime);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobToStop)).thenReturn(kubernetesWorkerJob);
        }

        doThrow(new ApiException("ApiException error")).when(workflowService).deleteWorkflowForJob(JOB_ID);

        kubernetesWorkerJobUpdatesManager.onWorkflowStopRequested(kubernetesWorkerJobToStop);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobToStop);

        inOrder.verify(workflowService).deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();

        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.ERROR);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(jobStartTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(testStartTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobEventCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getErrorDescription()).isEqualTo("ApiException error");
    }


    @Test
    @Ignore("Start request must be ignored only if it refers to the same jobProcessingID : https://proactioneu.ent.cgi.com/jira/browse/POIEO-1459")
    public void testOnWorkflowStartRequested_IgnoresStartRequest_WhenKubernetesWorkerJobExists() {

        when(kubernetesWorkerJobDataService.findByJobId(JOB_ID)).thenReturn(Optional.of(new KubernetesWorkerJob(JOB_ID, INT_JOB_ID)));

        kubernetesWorkerJobUpdatesManager.onWorkflowStartRequested(new KubernetesWorkerJob(JOB_ID, INT_JOB_ID), WORKFLOW_JOB_SPEC);

        inOrder.verify(kubernetesWorkerJobDataService).findByJobId(JOB_ID);
    }


    @Test
    public void testOnWorkflowStartRequested_CreatesK8sWorkerJobWithErrorStatusAndForwardsErrorEvent_WhenCreateLegacyWorkflowFails() throws Exception {

        OffsetDateTime testStartTime = OffsetDateTime.now();

        doThrow(new ApiException("ApiException error")).when(workflowService).createLegacyWorkflow(WORKFLOW_JOB_SPEC);

        kubernetesWorkerJobUpdatesManager.onWorkflowStartRequested(new KubernetesWorkerJob(JOB_ID, INT_JOB_ID), WORKFLOW_JOB_SPEC);

        inOrder.verify(kubernetesWorkerJobDataService).findByJobId(JOB_ID);

        inOrder.verify(workflowService).createLegacyWorkflow(WORKFLOW_JOB_SPEC);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService).save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.ERROR);
        assertThat(savedKubernetesWorkerJob.getStart()).isBetween(testStartTime, OffsetDateTime.now());
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(testStartTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("ApiException error");

    }

    @Test
    public void testOnWorkflowRunning_CreatesK8sWorkerJobWithStartedStatusAndForwardsProcessingStartedEvent_WhenKubernetesWorkerJobDoesNotExist() {

        KubernetesWorkerJob kubernetesWorkerJobInRunning = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobInRunning)).thenReturn(kubernetesWorkerJobInRunning);

        kubernetesWorkerJobUpdatesManager.onWorkflowRunning(kubernetesWorkerJobInRunning);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobInRunning);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isNull();
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(savedKubernetesWorkerJob.getStart()).isNull();
        assertThat(savedKubernetesWorkerJob.getEnd()).isNull();

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_STARTED);

    }

    @Test
    public void testOnWorkflowRunning_UpdatesK8sWorkerJobStatusToStartedAndForwardsProcessingStartedEvent_WhenKubernetesWorkerJobExists() {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobInRunning = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTING);
            kubernetesWorkerJob.setStart(startTime);
            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobInRunning)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowRunning(kubernetesWorkerJobInRunning);

        inOrder.verify(kubernetesWorkerJobDataService)
                .findByIdOrDefault(kubernetesWorkerJobInRunning);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isNull();

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_STARTED);

    }

    @Test
    public void testOnWorkflowRunning_UpdatesK8sWorkerJobStatusToStartedAndForwardsProcessingStartedEvent_WhenKubernetesWorkerJobExistsWithUnexpectedStatus() {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobInRunning = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.COMPLETED);
            kubernetesWorkerJob.setStart(startTime);
            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobInRunning)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowRunning(kubernetesWorkerJobInRunning);

        inOrder.verify(kubernetesWorkerJobDataService)
                .findByIdOrDefault(kubernetesWorkerJobInRunning);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isNull();

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_STARTED);

    }

    @Test
    public void testOnWorkflowCompleted_CreatesK8sWorkerJobWithStatusCompletedAndForwardsProcessingCompletedEvent_WhenKubernetesWorkerJobDoesNotExist() throws Exception {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobCompleted = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobCompleted)).thenReturn(new KubernetesWorkerJob(JOB_ID, INT_JOB_ID));

        kubernetesWorkerJobUpdatesManager.onWorkflowCompleted(kubernetesWorkerJobCompleted);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobCompleted);

        inOrder.verify(workflowService).deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isNull();
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.COMPLETED);
        assertThat(savedKubernetesWorkerJob.getStart()).isNull();
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_COMPLETED);

    }

    @Test
    public void testOnWorkflowCompleted_UpdatesK8sWorkerJobStatusToCompletedAndForwardsProcessingCompletedEvent_WhenKubernetesWorkerJobExists() throws Exception {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJob1Completed = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJob1Completed)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowCompleted(kubernetesWorkerJob1Completed);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJob1Completed);

        inOrder.verify(workflowService).deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.COMPLETED);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_COMPLETED);

    }

    @Test
    public void testOnWorkflowCompleted_UpdatesK8sWorkerJobStatusToErrorAndForwardsErrorEvent_WhenDeleteWorkflowForJobFails() throws Exception {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobCompleted = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobCompleted)).thenReturn(kubernetesWorkerJob);
        }

        doThrow(new ApiException("ApiException error")).when(workflowService).deleteWorkflowForJob(JOB_ID);

        kubernetesWorkerJobUpdatesManager.onWorkflowCompleted(kubernetesWorkerJobCompleted);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobCompleted);

        inOrder.verify(workflowService).deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.ERROR);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("ApiException error");

    }

    @Test
    public void testOnWorkflowDeleted_DoesNotUpdateK8sWorkerJobStatus_WhenK8sWorkerJobExists() throws Exception {

        KubernetesWorkerJob kubernetesWorkerJobToDelete = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(OffsetDateTime.now());
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.COMPLETED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobToDelete)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowDeleted(kubernetesWorkerJobToDelete);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobToDelete);

        inOrder.verify(workflowService).cleanUpWorkflowForJob(JOB_ID);

    }

    @Test
    public void testOnWorkflowDeleted_DoesNotUpdateK8sWorkerJobStatus_WhenK8sWorkerJobDoesNotExist() throws Exception {


        KubernetesWorkerJob kubernetesWorkerJobToDelete = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
        when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobToDelete)).thenReturn(kubernetesWorkerJobToDelete);

        kubernetesWorkerJobUpdatesManager.onWorkflowDeleted(kubernetesWorkerJobToDelete);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobToDelete);

        inOrder.verify(workflowService).cleanUpWorkflowForJob(JOB_ID);

    }

    @Test
    public void testOnWorkflowDeleted_UpdatesK8sWorkerJobStatusToErrorAndForwardsErrorEvent_WhenCleanUpWorkflowForJobFails() throws Exception {

        KubernetesWorkerJob kubernetesWorkerJobToDelete = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(OffsetDateTime.now());
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.COMPLETED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobToDelete)).thenReturn(kubernetesWorkerJob);
        }

        doThrow(new ApiException("ApiException error")).when(workflowService).cleanUpWorkflowForJob(JOB_ID);

        kubernetesWorkerJobUpdatesManager.onWorkflowDeleted(kubernetesWorkerJobToDelete);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobToDelete);

        inOrder.verify(workflowService).cleanUpWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();

        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.ERROR);

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("ApiException error");

    }

    @Test
    public void testOnWorkflowFailed_CreatesK8sWorkerJobWithStatusFailedAndForwardsErrorEvent_WhenDeleteOnFailureIsFalseAndK8sWorkerJobDoesNotExist() {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobFailed = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
        when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobFailed)).thenReturn(kubernetesWorkerJobFailed);

        kubernetesWorkerJobUpdatesManager.onWorkflowFailed(kubernetesWorkerJobFailed);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobFailed);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isNull();
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.FAILED);
        assertThat(savedKubernetesWorkerJob.getStart()).isNull();
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("Unknown failure");
    }

    @Test
    public void testOnWorkflowFailed_UpdatesK8sWorkerJobStatusToFailedAndForwardsErrorEvent_WhenDeleteOnFailureIsFalseAndK8sWorkerJobExists() {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobFailed = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobFailed)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowFailed(kubernetesWorkerJobFailed);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobFailed);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.FAILED);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("Unknown failure");
    }

    @Test
    public void testOnWorkflowFailed_DeletesWorkflowForJobUpdatesK8sWorkerJobStatusToFailedAndForwardsErrorEvent_WhenDeleteOnFailureIsTrue() throws Exception {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobFailed = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobFailed)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager = new KubernetesWorkerJobUpdatesManagerImpl(
                workflowService, jobUpdateListener, kubernetesWorkerJobDataService, true);

        kubernetesWorkerJobUpdatesManager.onWorkflowFailed(kubernetesWorkerJobFailed);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobFailed);

        inOrder.verify(workflowService).deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.FAILED);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("Unknown failure");

    }

    @Test
    public void testOnWorkflowFailed_UpdatesK8sWorkerJobStatusToErrorAndForwardsErrorEvent_WhenDeleteWorkflowForJobFails() throws Exception {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJobFailed = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

            when(kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJobFailed)).thenReturn(kubernetesWorkerJob);
        }

        kubernetesWorkerJobUpdatesManager = new KubernetesWorkerJobUpdatesManagerImpl(
                workflowService, jobUpdateListener, kubernetesWorkerJobDataService, true);

        doThrow(new ApiException("ApiException error")).when(workflowService).deleteWorkflowForJob(JOB_ID);

        kubernetesWorkerJobUpdatesManager.onWorkflowFailed(kubernetesWorkerJobFailed);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(kubernetesWorkerJobFailed);

        inOrder.verify(workflowService).deleteWorkflowForJob(JOB_ID);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.ERROR);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobError> jobErrorCaptor = ArgumentCaptor.forClass(JobError.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobErrorCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobErrorCaptor.getValue().getErrorDescription()).isEqualTo("ApiException error");

    }

    @Test
    public void testOnWorkflowNotFound_CreatesK8sWorkerJobWithStatusNotAvailableAndNoJobTypeAndForwardsCompletedEvent_WhenK8sWorkerJobDoesNotExist() {

        OffsetDateTime startTime = OffsetDateTime.now();
        KubernetesWorkerJob inputK8WorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);

        when(kubernetesWorkerJobDataService.findByIdOrDefault(inputK8WorkerJob)).thenReturn(inputK8WorkerJob);

        kubernetesWorkerJobUpdatesManager.onWorkflowNotFound(inputK8WorkerJob);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(inputK8WorkerJob);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.NOT_AVAILABLE);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());
        assertThat(savedKubernetesWorkerJob.getJobType()).isNull();
        assertThat(savedKubernetesWorkerJob.getStart()).isNull();

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_COMPLETED);
    }

    @Test
    public void testOnWorkflowNotFound_UpdatesK8sWorkerJobWithStatusNotAvailableAndForwardsCompletedEvent_WhenK8sWorkerJobExists() {

        OffsetDateTime startTime = OffsetDateTime.now();

        KubernetesWorkerJob inputK8WorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
        {
            KubernetesWorkerJob dbK8WorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            dbK8WorkerJob.setStart(startTime);
            dbK8WorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            dbK8WorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);
            when(kubernetesWorkerJobDataService.findByIdOrDefault(inputK8WorkerJob)).thenReturn(dbK8WorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowNotFound(inputK8WorkerJob);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(inputK8WorkerJob);

        ArgumentCaptor<KubernetesWorkerJob> kubernetesWorkerJobCaptor = ArgumentCaptor.forClass(KubernetesWorkerJob.class);

        inOrder.verify(kubernetesWorkerJobDataService)
                .save(kubernetesWorkerJobCaptor.capture());

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobCaptor.getValue();
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(savedKubernetesWorkerJob.getJobType()).isEqualTo(KubernetesWorkerJob.JobType.WORKFLOW);
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.NOT_AVAILABLE);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isBetween(startTime, OffsetDateTime.now());

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.PROCESSING_COMPLETED);
    }

    @Test
    public void testOnWorkflowHeartBeat_ForwardsHeartbeatEvent_WhenK8sWorkerJobExists() {

        OffsetDateTime startTime = OffsetDateTime.now();
        KubernetesWorkerJob inputK8WorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
        inputK8WorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        inputK8WorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);
        {
            KubernetesWorkerJob dbK8WorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
            dbK8WorkerJob.setStart(startTime);
            dbK8WorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
            dbK8WorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);
            when(kubernetesWorkerJobDataService.findByIdOrDefault(inputK8WorkerJob)).thenReturn(dbK8WorkerJob);
        }

        kubernetesWorkerJobUpdatesManager.onWorkflowHeartBeat(inputK8WorkerJob);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(inputK8WorkerJob);

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.HEARTBEAT);
    }

    @Test
    public void testOnWorkflowHeartBeat_ForwardsHeartbeatEvent_WhenK8sWorkerJobDoesNotExist() {

        KubernetesWorkerJob inputK8WorkerJob = new KubernetesWorkerJob(JOB_ID, INT_JOB_ID);
        inputK8WorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        inputK8WorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

        when(kubernetesWorkerJobDataService.findByIdOrDefault(inputK8WorkerJob)).thenReturn(inputK8WorkerJob);

        kubernetesWorkerJobUpdatesManager.onWorkflowHeartBeat(inputK8WorkerJob);

        inOrder.verify(kubernetesWorkerJobDataService).findByIdOrDefault(inputK8WorkerJob);

        ArgumentCaptor<JobEvent> jobEventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        ArgumentCaptor<String> intJobIdCaptor = ArgumentCaptor.forClass(String.class);

        inOrder.verify(jobUpdateListener)
                .jobUpdate(intJobIdCaptor.capture(), jobEventCaptor.capture());

        assertThat(intJobIdCaptor.getValue()).isEqualTo(INT_JOB_ID);
        assertThat(jobEventCaptor.getValue().getJobEventType()).isEqualTo(JobEventType.HEARTBEAT);
    }
}
