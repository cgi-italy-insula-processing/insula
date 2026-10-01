package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.google.protobuf.Timestamp;

/**
 * Handles the job update events driving a job through its life cycle.
 */
public interface WorkerJobUpdatesManager {

    /**
     * Handle the start of the input data fetching for a job.
     *
     * @param job the job whose data fetching started
     * @param workerId the identifier of the worker running the job
     */
    void onJobDataFetchingStarted(Job job, String workerId);

    /**
     * Handle the completion of the input data fetching for a job.
     *
     * @param job the job whose data fetching completed
     */
    void onJobDataFetchingCompleted(Job job);

    /**
     * Handle the start of the processing for a job.
     *
     * @param job the job whose processing started
     * @param timestamp the time at which the processing started
     * @param backendServiceLocation the location of the backend service, if supported
     */
    void onJobProcessingStarted(Job job, Timestamp timestamp, String backendServiceLocation);

    /**
     * Handle the completion of the processing for a job.
     *
     * @param job the job whose processing completed
     * @param timestamp the time at which the processing completed
     */
    void onJobProcessingCompleted(Job job, Timestamp timestamp);

    /**
     * Handle a heartbeat received for a running job.
     *
     * @param job the job the heartbeat was received for
     * @param timestamp the time of the heartbeat
     */
    void onJobHeartbeat(Job job, Timestamp timestamp);

    /**
     * Handle an error reported for a job.
     *
     * @param job the job the error was reported for
     * @param description the error description
     */
    void onJobError(Job job, String description);

    /**
     * Handle an error raised while processing a job update.
     *
     * @param job the job the error occurred for
     * @param throwable the error
     */
    void onJobError(Job job, Throwable throwable);
}
