package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import java.io.IOException;

import com.cgi.eoss.platform.core.processing.server.model.Job;

/**
 * Processes the outputs of a job.
 */
public interface OutputProcessor {

    /**
     * Process the outputs of the given job.
     *
     * @param job the job whose outputs must be processed
     * @throws IOException if an output cannot be processed
     */
    void processOutputs(Job job) throws IOException;
}
