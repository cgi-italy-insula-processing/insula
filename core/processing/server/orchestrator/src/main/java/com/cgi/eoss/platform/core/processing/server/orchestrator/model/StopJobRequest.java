package com.cgi.eoss.platform.core.processing.server.orchestrator.model;

import lombok.Builder;
import lombok.Value;

/**
 * Request to stop a job.
 */
@Value
@Builder
public class StopJobRequest {
    String intJobId;
}
