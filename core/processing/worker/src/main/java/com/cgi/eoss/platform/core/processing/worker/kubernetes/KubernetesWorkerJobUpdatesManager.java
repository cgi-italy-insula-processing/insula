package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;

/**
 * Interface that defines methods for handling Workflow and ApplicationPod events
 */
public interface KubernetesWorkerJobUpdatesManager {

    /**
     * Handle the workflow start request event
     *
     * @param k8WorkerJob The event to handle
     * @param jobSpec the job specification
     */
    void onWorkflowStartRequested(KubernetesWorkerJob k8WorkerJob, JobSpec jobSpec);

    /**
     * Handle the workflow running event
     *
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowRunning(KubernetesWorkerJob k8WorkerJob);

    /**
     * Handle the workflow completed event
     *
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowCompleted(KubernetesWorkerJob k8WorkerJob);

    /**
     * Handle the workflow deleted event
     *
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowDeleted(KubernetesWorkerJob k8WorkerJob);

    /**
     * Handle the workflow failed event
     *
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowFailed(KubernetesWorkerJob k8WorkerJob);

    /**
     * Handle the workflow not found event
     *
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowNotFound(KubernetesWorkerJob k8WorkerJob);

    /**
     * Handle the workflow stop request event
     *
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowStopRequested(KubernetesWorkerJob k8WorkerJob);

    /**
     * Handle the workflow heartbeat request event
     * @param k8WorkerJob The event to handle
     */
    void onWorkflowHeartBeat(KubernetesWorkerJob k8WorkerJob);
}
