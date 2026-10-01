package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;

/**
 * Exposes functionality to calculate the priority for {@link Job}s
 */
public interface JobPriorityCalculator {

    /**
     * Calculate the value for the job priority
     * @param job the job for which the priority should be calculated
     * @return an int representing the job priority
     */
    int calculateJobPriority(Job job);
}
