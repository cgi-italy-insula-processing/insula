package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.rpc.GrpcUtil;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.worker.JobError;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service.KubernetesWorkerJobDataService;
import io.kubernetes.client.openapi.ApiException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * This class handles the events related to the kubernetesWorkerJob resources by updating the jobs details and notifying the jobUpdateListener
 */
@Log4j2
@AllArgsConstructor
public class KubernetesWorkerJobUpdatesManagerImpl implements KubernetesWorkerJobUpdatesManager {

    public static final String APP_POD_RUNNING = "App Pod Running";
    private static final String WORKFLOW_RUNNING = "Workflow Running";
    private final WorkflowService workflowService;
    private final JobUpdateListener jobUpdateListener;
    private final KubernetesWorkerJobDataService kubernetesWorkerJobDataService;
    private final boolean deleteOnFailure;

    @Override
    public void onWorkflowStartRequested(KubernetesWorkerJob k8WorkerJob, JobSpec jobSpec) {
        Optional<KubernetesWorkerJob> retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByJobId(k8WorkerJob.getJobId());

        if (retrievedK8WorkerJob.isPresent()) {
            // TODO: the start request should be discarded once jobProcessingId will be part of the KubernetesWorkerJob PK
            // https://proactioneu.ent.cgi.com/jira/browse/POIEO-1459
            LOG.info("Unexpected 'Workflow Start' request for job {} in status {}",
                        // LOG.warn("Ignoring unexpected 'Workflow Start' request for job {} in status {}",
                        retrievedK8WorkerJob.get().getJobId(),
                        retrievedK8WorkerJob.get().getStatus());
            // return;
        }

        k8WorkerJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        k8WorkerJob.setStatus(KubernetesWorkerJob.Status.STARTING);
        k8WorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJobDataService.save(k8WorkerJob);

        try {
            workflowService.createLegacyWorkflow(jobSpec);
        } catch (IOException | ApiException e) {
            LOG.error("Failed to handle 'Workflow Start' request for job {}", k8WorkerJob.getJobId(), e);

            k8WorkerJob.setStatus(KubernetesWorkerJob.Status.ERROR);
            k8WorkerJob.setEnd(OffsetDateTime.now());
            kubernetesWorkerJobDataService.save(k8WorkerJob);

            sendErrorUpdate(k8WorkerJob.getIntJobId(), e.getMessage());
            return;
        }

        sendUpdate(k8WorkerJob.getIntJobId(), JobEventType.DATA_FETCHING_STARTED);
    }

    @Override
    public void onWorkflowRunning(KubernetesWorkerJob k8WorkerJob) {
        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (retrievedK8WorkerJob.isStartedStatusTransitionAllowed()) {
            LOG.error("Unexpected '{}' event for job {} in status {}", WORKFLOW_RUNNING, retrievedK8WorkerJob.getJobId(), retrievedK8WorkerJob.getStatus());
        }

        setKubernetesWorkerJobStatusToStarted(retrievedK8WorkerJob);
        sendUpdate(retrievedK8WorkerJob.getIntJobId(), JobEventType.PROCESSING_STARTED);
    }

    @Override
    public void onWorkflowCompleted(KubernetesWorkerJob k8WorkerJob) {
        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (KubernetesWorkerJob.Status.STARTED != retrievedK8WorkerJob.getStatus()) {
            LOG.error("Unexpected 'Workflow Completed' event for job {} in status {}", retrievedK8WorkerJob.getJobId(), retrievedK8WorkerJob.getStatus());
        }

        retrievedK8WorkerJob.setEnd(OffsetDateTime.now());

        try {
            workflowService.deleteWorkflowForJob(retrievedK8WorkerJob.getJobId());
        } catch (ApiException | IOException e) {
            LOG.error("Failed to handle 'Workflow Completed' event for job {}", retrievedK8WorkerJob.getJobId(), e);

            retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.ERROR);
            kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

            sendErrorUpdate(retrievedK8WorkerJob.getIntJobId(), e.getMessage());
            return;
        }

        retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.COMPLETED);
        kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

        sendUpdate(retrievedK8WorkerJob.getIntJobId(), JobEventType.PROCESSING_COMPLETED);
    }

    @Override
    public void onWorkflowDeleted(KubernetesWorkerJob k8WorkerJob) {
        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (KubernetesWorkerJob.Status.COMPLETED != retrievedK8WorkerJob.getStatus() &&
                    KubernetesWorkerJob.Status.FAILED != retrievedK8WorkerJob.getStatus() &&
                    KubernetesWorkerJob.Status.STOPPED != retrievedK8WorkerJob.getStatus()) {
            LOG.error("Unexpected 'Workflow Deleted' event for job {} in status {}", retrievedK8WorkerJob.getJobId(), retrievedK8WorkerJob.getStatus());
        }

        try {
            workflowService.cleanUpWorkflowForJob(retrievedK8WorkerJob.getJobId());
        } catch (ApiException | IOException e) {
            LOG.error("Failed to handle 'Workflow Deleted' event for job {}", retrievedK8WorkerJob.getJobId(), e);

            retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.ERROR);
            kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

            sendErrorUpdate(retrievedK8WorkerJob.getIntJobId(), e.getMessage());
        }
    }

    @Override
    public void onWorkflowFailed(KubernetesWorkerJob k8WorkerJob) {
        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (KubernetesWorkerJob.Status.STARTED != retrievedK8WorkerJob.getStatus()) {
            LOG.error("Unexpected 'Workflow Failed' event for job {} in status {}", retrievedK8WorkerJob.getJobId(), retrievedK8WorkerJob.getStatus());
        }

        retrievedK8WorkerJob.setEnd(OffsetDateTime.now());

        try {
            if (deleteOnFailure) {
                workflowService.deleteWorkflowForJob(retrievedK8WorkerJob.getJobId());
            }
        } catch (ApiException | IOException e) {
            LOG.error("Failed to handle 'Workflow Failed' event for job {}", retrievedK8WorkerJob.getJobId(), e);

            retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.ERROR);
            kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

            sendErrorUpdate(retrievedK8WorkerJob.getIntJobId(), e.getMessage());
            return;
        }

        retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.FAILED);
        kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

        sendErrorUpdate(retrievedK8WorkerJob.getIntJobId(), "Unknown failure");
    }

    @Override
    public void onWorkflowNotFound(KubernetesWorkerJob k8WorkerJob) {

        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (isWorkerJobRunning(retrievedK8WorkerJob)) {
            LOG.error("Unexpected 'Workflow Not Found' event for job {} in status {}",
                    retrievedK8WorkerJob.getJobId(), retrievedK8WorkerJob.getStatus());
        }

        retrievedK8WorkerJob.setEnd(OffsetDateTime.now());
        retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.NOT_AVAILABLE);
        kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

        sendUpdate(retrievedK8WorkerJob.getIntJobId(), JobEventType.PROCESSING_COMPLETED);
    }

    @Override
    public void onWorkflowStopRequested(KubernetesWorkerJob k8WorkerJob) {
        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (!retrievedK8WorkerJob.isStoppedStatusTransitionAllowed()) {
            LOG.warn("Ignoring unexpected 'Workflow Stop' request for job {} in status {}",
                        retrievedK8WorkerJob.getJobId(),
                        retrievedK8WorkerJob.getStatus());
            return;
        }

        retrievedK8WorkerJob.setEnd(OffsetDateTime.now());

        try {
            workflowService.deleteWorkflowForJob(retrievedK8WorkerJob.getJobId());
        } catch (IOException | ApiException e) {
            LOG.error("Failed to handle 'Workflow Stop' request for job {}", retrievedK8WorkerJob.getJobId(), e);

            retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.ERROR);
            kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

            sendErrorUpdate(retrievedK8WorkerJob.getIntJobId(), e.getMessage());
            return;
        }

        retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.STOPPED);
        kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);

        sendUpdate(retrievedK8WorkerJob.getIntJobId(), JobEventType.PROCESSING_COMPLETED);
    }

    @Override
    public void onWorkflowHeartBeat(KubernetesWorkerJob k8WorkerJob) {

        KubernetesWorkerJob retrievedK8WorkerJob = kubernetesWorkerJobDataService.findByIdOrDefault(k8WorkerJob);

        if (isWorkerJobRunning(retrievedK8WorkerJob)) {
            LOG.error("Unexpected 'Workflow HeartBeat' event for job {} in status {}",
                    retrievedK8WorkerJob.getJobId(), retrievedK8WorkerJob.getStatus());
        }

        sendUpdate(retrievedK8WorkerJob.getIntJobId(), JobEventType.HEARTBEAT);
    }

    private void setKubernetesWorkerJobStatusToStarted(KubernetesWorkerJob retrievedK8WorkerJob) {
        retrievedK8WorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);
        kubernetesWorkerJobDataService.save(retrievedK8WorkerJob);
    }

    private void sendUpdate(String intJobId, JobEventType jobEventType) {
        jobUpdateListener.jobUpdate(intJobId,
                    JobEvent.newBuilder().setJobEventType(jobEventType).setTimestamp(GrpcUtil.timestampFromOffsetDateTime(OffsetDateTime.now())).build());
    }

    private void sendErrorUpdate(String intJobId, String message) {
        jobUpdateListener.jobUpdate(intJobId,
                    JobError.newBuilder().setErrorDescription(message).build());
    }

    private static boolean isWorkerJobRunning(KubernetesWorkerJob retrievedK8WorkerJob) {
        return KubernetesWorkerJob.Status.STARTED != retrievedK8WorkerJob.getStatus() &&
                KubernetesWorkerJob.Status.STARTING != retrievedK8WorkerJob.getStatus();
    }
}
