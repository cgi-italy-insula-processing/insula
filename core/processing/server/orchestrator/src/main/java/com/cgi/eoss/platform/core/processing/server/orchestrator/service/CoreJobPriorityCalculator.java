package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;

/**
 * No-op implementation for {@link JobPriorityCalculator}
 */
public class CoreJobPriorityCalculator implements JobPriorityCalculator {

    private static final int DEFAULT_PRIORITY = 1;

    /**
     * No-op implementation that always returns a default value regardless the input
     * @param job the job input argument
     * @return a default value equal to 1
     */
    @Override
    public int calculateJobPriority(Job job) {
        return DEFAULT_PRIORITY;
    }
}
