package com.cgi.eoss.platform.core.processing.worker.kubernetes;

public interface JobUpdateListener {

    /**
     * Invoked when a worker job has been updated
     *
     * @param jobIntId the id of the job that has been updated
     * @param update the update details
     */
    void jobUpdate(String jobIntId, Object update);

}