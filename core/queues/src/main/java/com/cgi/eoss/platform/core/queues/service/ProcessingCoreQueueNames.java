package com.cgi.eoss.platform.core.queues.service;

/**
 * Class that exposes queue names that belong to the core processing domain
 */
public final class ProcessingCoreQueueNames {

    /*
     * Name of the job execution requests queue
     */
    public static final String JOB_EXECUTION = "platform-jobs";

    /*
     * Name of the job stop requests queue
     */
    public static final String JOB_STOP_REQUESTS ="platform-job-stop-requests";

    /*
     * Name of the job updates queue
     */
    public static final String JOB_UPDATES =  "platform-jobs-updates";

    /*
     * Name of the job related kubernetes events queue
     */
    public static final String KUBERNETES_EVENTS = "platform-kubernetes-events";

    private ProcessingCoreQueueNames() {
    }
}
