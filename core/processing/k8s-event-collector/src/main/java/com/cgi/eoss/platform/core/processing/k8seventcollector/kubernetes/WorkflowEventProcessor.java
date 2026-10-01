package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;


import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.utils.WorkflowUtils;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import io.kubernetes.client.util.Watch.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor of Kubernetes Argo Workflow events.
 * This implementation publishes messages on a queue for relevant events.
 *
 * @author cantaveneraf
 *
 */
@Slf4j
@AllArgsConstructor
public class WorkflowEventProcessor implements KubernetesEventProcessor<Response<Object>> {

    private final QueueService queueService;

    @Override
    public void process(Response<Object> event) {
        try {
            LOG.info("Workflow Event - Type: {} - item object class: {}", event.type, event.object.getClass());
            Workflow workflow = WorkflowUtils.deserializeWorkflow(event.object);
            String workflowType = workflow.getMetadata().getLabels().getOrDefault(CorePlatformLabels.PLATFORM_WORKFLOW_TYPE_LABEL, "unknown");
            if (!"job".equals(workflowType)) {
                LOG.info("Ignoring event for workflow type {}", workflowType);
                return;
            }
            String jobId = workflow.getMetadata().getLabels().get(CorePlatformLabels.PLATFORM_JOB_ID_LABEL);
            String intJobId = workflow.getMetadata().getLabels().get(CorePlatformLabels.PLATFORM_INT_JOB_ID_LABEL);
            if (jobId == null || intJobId == null) {
                LOG.info("Ignoring workflow event with null job identifiers: jobId '{}' intJobId: '{}'", jobId, intJobId);
                return;
            }
            String workflowPhase = WorkflowPhases.UNKNOWN;
            if (workflow.getStatus() != null && workflow.getStatus().getPhase() != null) {
                workflowPhase = workflow.getStatus().getPhase();
            }

            if ("MODIFIED".equals(event.type)) {
                handleModifiedWorkflow(jobId, intJobId, workflowPhase);
            } else if ("DELETED".equals(event.type)) {
                LOG.info("Send k8s event: workflow deleted for job {}", jobId);
                queueService.sendObject(ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                            K8SEvent.newBuilder().setEventType(K8SEventType.K8S_WORKFLOW_DELETED).setJobId(jobId).setIntJobId(intJobId).setResourceStatus(workflow.getStatus().getPhase()).build());
            } else {
                LOG.info("Ignoring workflow event type: '{}'", event.type);
            }
        } catch (Exception e) {
            LOG.error("Error while processing workflow event", e);
        }
    }

    private void handleModifiedWorkflow(String jobId, String intJobId, String phase) {
        switch (phase) {
            case WorkflowPhases.SUCCEEDED:
                LOG.info("Send k8s event: workflow completed for job {}", jobId);
                queueService.sendObject(ProcessingCoreQueueNames.KUBERNETES_EVENTS, K8SEvent.newBuilder().setEventType(K8SEventType.K8S_WORKFLOW_COMPLETED).setJobId(jobId).setIntJobId(intJobId).build());
                break;
            case WorkflowPhases.RUNNING:
                LOG.info("Ignoring workflow running event for job {}:", jobId);
                break;
            case WorkflowPhases.FAILED:
                LOG.info("Send k8s event: workflow failed for job {}", jobId);
                queueService.sendObject(ProcessingCoreQueueNames.KUBERNETES_EVENTS, K8SEvent.newBuilder().setEventType(K8SEventType.K8S_WORKFLOW_FAILED).setJobId(jobId).setIntJobId(intJobId).build());
                break;
            default:
                LOG.info("Ignoring modified event for workflow phase '{}'", phase);
                break;
        }
    }
}
