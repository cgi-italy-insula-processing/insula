package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Workflow;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;

/**
 * Interface that defines the method to create an Argo 'legacy' Workflow.
 *
 * @author cantaveneraf
 *
 */
public interface LegacyWorkflowBuilder {

    /**
     * Create an Argo 'legacy' Workflow
     *
     * @param jobSpec
     *            The job specification
     * @param existingClaimName
     *            Claim of the main volume
     * @return
     *         An instance of Argo 'legacy' Workflow
     */
    Workflow getLegacyWorkflow(JobSpec jobSpec, String existingClaimName);

    /**
     * Create the Persistent Volume Claim for the provided jobSpec
     *
     * @param jobSpec
     *            The job specification
     * @return
     *         An instance of the PVC
     */
    V1PersistentVolumeClaim getPersistentVolumeClaim(JobSpec jobSpec);

    /**
     * Create the label selector to retrieve the Kubernetes resources
     * associated to the given job identifier
     *
     * @param jobId
     *            The job identifier
     *
     * @return
     *         The label selector
     */
    String getLabelSelector(String jobId);
}
