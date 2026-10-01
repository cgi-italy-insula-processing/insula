package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.google.common.collect.Iterables;

/**
 * Extracts platform parameters shared by core orchestrator components.
 */
public abstract class CorePlatformParameterExtractor {

    private static final String TIMEOUT_PARAM = "timeout";

    protected CorePlatformParameterExtractor() {
    }

    /**
     * Extracts the timeout configured for the provided job.
     * @param job the job whose timeout should be extracted
     * @return the configured timeout, or {@code 0} when it is not provided
     */
    public static Integer getTimeout(Job job) {
        return Integer.valueOf(Iterables.getOnlyElement(job.getConfig().getInputs().get(TIMEOUT_PARAM), "0"));
    }
}
