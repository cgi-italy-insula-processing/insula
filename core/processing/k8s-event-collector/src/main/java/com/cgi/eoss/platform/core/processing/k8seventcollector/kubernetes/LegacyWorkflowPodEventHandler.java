package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Handles Kubernetes Pod Event related to Legacy Workflows
 */
@Log4j2
@AllArgsConstructor
public class LegacyWorkflowPodEventHandler implements PodEventHandler {
    private static final String POD_CONDITION_TYPE_INITIALIZED = "Initialized";
    private static final String POD_CONDITION_STATUS_TRUE = "True";

    private final QueueService queueService;

    /**
     * Returns true if the Kubernetes Pod Event to handle is related to a Legacy Workflow
     * @param podEvent the Kubernetes Pod Event to verify if supported
     * @return true if the Kubernetes Pod Event is related to a Legacy Workflow
     */
    @Override
    public boolean supports(Watch.Response<V1Pod> podEvent) {
        return hasWorkflowStepLabel(podEvent);
    }

    /**
     * Handles a Kubernetes Pod Event related to a Legacy Workflow by publishing
     * a message on a queue if the event is relevant
     * @param podEvent the Legacy Workflow Kubernetes Pod Event to handle
     */
    @Override
    public void handlePodEvent(Watch.Response<V1Pod> podEvent) {
        if(!supports(podEvent)){
            LOG.info("Unsupported Pod Event - Type : {} - Object : {}", podEvent.type, podEvent.object);
            return;
        }

        if (isProcessingWorkflowPodEvent(podEvent)) {
            handleProcessingPodEvent(podEvent.object);
        }
    }

    private void handleProcessingPodEvent(V1Pod pod ) {

        if (PodPhases.PENDING.equals(resolvePodPhase(pod)) && isInitialized(pod)) {
            String jobId = pod.getMetadata().getLabels().get(CorePlatformLabels.PLATFORM_JOB_ID_LABEL);
            String intJobId = pod.getMetadata().getLabels().get(CorePlatformLabels.PLATFORM_INT_JOB_ID_LABEL);
            LOG.info("Send k8s event: workflow running (processing started) for job {}", jobId);
            dispatchKubernetesEvent(K8SEvent.newBuilder()
                    .setEventType(K8SEventType.K8S_WORKFLOW_RUNNING)
                    .setJobId(jobId)
                    .setIntJobId(intJobId)
                    .build());
        }
    }

    private void dispatchKubernetesEvent(K8SEvent event) {
        queueService.sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                event);
    }

    private static boolean hasWorkflowStepLabel(Watch.Response<V1Pod> podEvent) {
        return getWorkflowStepLabel(podEvent) != null;
    }

    private static boolean isProcessingWorkflowPodEvent(Watch.Response<V1Pod> podEvent) {
        return CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE.equals(getWorkflowStepLabel(podEvent));
    }

    private static String getWorkflowStepLabel(Watch.Response<V1Pod> podEvent) {
        return podEvent.object.getMetadata().getLabels().get(CorePlatformLabels.PLATFORM_WORKFLOW_STEP_LABEL);
    }

    private static String resolvePodPhase(V1Pod pod) {
        if (pod.getStatus() != null && pod.getStatus().getPhase() != null) {
            return pod.getStatus().getPhase();
        }
        return PodPhases.UNKNOWN;
    }

    private static boolean isInitialized(V1Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        return pod.getStatus().getConditions()
                .stream()
                .anyMatch(condition ->
                        POD_CONDITION_TYPE_INITIALIZED.equals(condition.getType()) &&
                        POD_CONDITION_STATUS_TRUE.equals(condition.getStatus()));
    }

}
