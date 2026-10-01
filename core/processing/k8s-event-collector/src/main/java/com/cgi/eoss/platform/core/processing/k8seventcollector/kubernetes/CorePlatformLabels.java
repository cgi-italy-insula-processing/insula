package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

/**
 * Known labels assigned to Kubernetes platform resources
 *
 *
 */
public class CorePlatformLabels {

    /**
     * Label for the job IDs
     */
    public static final String PLATFORM_JOB_ID_LABEL = "platform/jobid";

    /**
     * Label for the job integer IDs
     */
    public static final String PLATFORM_INT_JOB_ID_LABEL = "platform/intjobid";

    /**
     * Label for the workflow type
     */
    public static final String PLATFORM_WORKFLOW_TYPE_LABEL = "platform/workflow-type";

    /**
     * Label to identify the workflow step of a pod
     */
    public static final String PLATFORM_WORKFLOW_STEP_LABEL = "platform/workflow-step";

    /**
     * Workflow step label value indicating the processing phase
     */
    public static final String WORKFLOW_STEP_PROCESSING_VALUE = "processing";
}
