package com.cgi.eoss.platform.core.processing.worker.kubernetes;


import com.cgi.eoss.platform.core.processing.rpc.GrpcUtil;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.StopJob;
import com.google.protobuf.Timestamp;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;

import javax.jms.JMSException;


/**
 * Dispatcher of JMS messages related to Kubernetes worker job execution, stop requests, and workflow events.
 */
@Slf4j
@AllArgsConstructor
public class KubernetesCoreDispatcher {

    private final LegacyWorkflowEventsDispatcher legacyWorkflowEventsDispatcher;
    private final KubernetesWorkerJobUpdatesManager kubernetesWorkerJobUpdatesManager;

    /**
     * Receives a job execution request and forwards supported workflow jobs to the Kubernetes worker job manager.
     *
     * @param jobSpec specification of the job to execute
     * @throws JMSException if the JMS infrastructure fails while delivering the message
     */
    @JmsListener(destination = ProcessingCoreQueueNames.JOB_EXECUTION)
    public void receiveJobSpec(JobSpec jobSpec) throws JMSException {

        logWorkerJobStartRequestedEvent(this, jobSpec);

        Kind kind = jobSpec.getKind();
        if (kind == Kind.WORKFLOW) {
            launchLegacyWorkflow(jobSpec);
        } else {
            LOG.warn("Received JobSpec with unrecognized kind: {}", kind);
        }
    }

    /**
     * Receives a job stop request and forwards supported workflow jobs to the Kubernetes worker job manager.
     *
     * @param stopJob request identifying the job to stop
     * @throws JMSException if the JMS infrastructure fails while delivering the message
     */
    @JmsListener(destination = ProcessingCoreQueueNames.JOB_STOP_REQUESTS)
    public void receiveStopJobRequest(StopJob stopJob) throws JMSException {

        logWorkerJobStopRequestedEvent(this, stopJob);
        Kind kind = stopJob.getKind();
        if (kind == Kind.WORKFLOW) {
            stopWorkflow(stopJob.getJob());
        } else {
            LOG.warn("Received stopJob with unrecognized kind: {}", kind);
        }
    }

    /**
     * Receives a Kubernetes event and delegates it to the legacy workflow events dispatcher.
     *
     * @param event Kubernetes event associated with a worker job workflow
     */
    @JmsListener(destination = ProcessingCoreQueueNames.KUBERNETES_EVENTS)
    public void receiveKubernetesEvent(K8SEvent event) {

        logWorkerJobUpdateReceivedEvent(this, event);
        legacyWorkflowEventsDispatcher.dispatch(event);
    }

    private void launchLegacyWorkflow(JobSpec jobSpec) {
        LOG.info("Received Start Legacy Workflow Request {}", jobSpec.getJob().getId());
        kubernetesWorkerJobUpdatesManager.onWorkflowStartRequested(
            new KubernetesWorkerJob(jobSpec.getJob().getId(), jobSpec.getJob().getIntJobId()), jobSpec);
    }

    private void stopWorkflow(Job job) {
        LOG.info("Received Stop Legacy Workflow Request {}", job.getId());
        kubernetesWorkerJobUpdatesManager.onWorkflowStopRequested(
            new KubernetesWorkerJob(job.getId(), job.getIntJobId()));
    }

    private static void logWorkerJobUpdateReceivedEvent(Object source, K8SEvent event) {

        LOG.info("Worker Job Update Received Event: source={}, jobId={}, jobExtId={}, eventType={} resourceStatus={} updateTimestamp={}",
            source.getClass().getCanonicalName(),
            event.getJobId(),
            event.getJobId(),
            event.getEventType(),
            event.getResourceStatus(),
            toEpochSecondOrNull(event.getTimestamp())
        );
    }

    private static void logWorkerJobStartRequestedEvent(Object source, JobSpec jobSpec) {

        Job job = jobSpec.getJob();
        String ownerIdentifier = job.hasUserUUID() ? job.getUserUUID()
            : job.getUserId();
        Service service = jobSpec.getService();

        LOG.info("Worker Job Start Requested Event: source={}, jobId={}, jobExtId={}, jobOwner={}, serviceId={}, serviceName={}, serviceDockerTag={}, processorType={}",
            source.getClass().getCanonicalName(),
            job.getIntJobId(),
            job.getId(),
            ownerIdentifier,
            service.getId(),
            service.getName(),
            service.getDockerImageTag(),
            jobSpec.getKind());
    }

    private static void logWorkerJobStopRequestedEvent(Object source, StopJob stopJob) {

        Job job = stopJob.getJob();
        LOG.info("Worker Job Stop Requested Event: source={}, jobId={}, jobExtId={}, serviceId={}, processorType={}",
            source.getClass().getCanonicalName(),
            job.getIntJobId(),
            job.getId(),
            job.getServiceId(),
            stopJob.getKind());

    }

    private static Long toEpochSecondOrNull(Timestamp timestamp) {

        return  timestamp != null ? GrpcUtil.offsetDateTimeFromTimestamp(timestamp).toEpochSecond() : null;
    }

}
