package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link KubernetesEventDispatcher} in charge of the lifecycle events of legacy workflow resources.
 */
@Slf4j
@AllArgsConstructor
public class LegacyWorkflowEventsDispatcher implements KubernetesEventDispatcher {

    private final KubernetesWorkerJobUpdatesManager kubernetesWorkerJobUpdatesManager;

    @Override
    public boolean dispatch(K8SEvent event) {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob(event.getJobId(), event.getIntJobId());

        switch (event.getEventType()) {
            case K8S_WORKFLOW_RUNNING:
                kubernetesWorkerJobUpdatesManager.onWorkflowRunning(kubernetesWorkerJob);
                break;
            case K8S_WORKFLOW_COMPLETED:
                kubernetesWorkerJobUpdatesManager.onWorkflowCompleted(kubernetesWorkerJob);
                break;
            case K8S_WORKFLOW_DELETED:
                kubernetesWorkerJobUpdatesManager.onWorkflowDeleted(kubernetesWorkerJob);
                break;
            case K8S_WORKFLOW_FAILED:
                kubernetesWorkerJobUpdatesManager.onWorkflowFailed(kubernetesWorkerJob);
                break;
            default:
                LOG.warn("LegacyWorkflowEventsDispatcher unrecognized event type {}", event.getEventType());
                return false;
        }

        return true;
    }

}
