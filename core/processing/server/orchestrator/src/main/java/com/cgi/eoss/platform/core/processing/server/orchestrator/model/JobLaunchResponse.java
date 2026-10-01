package com.cgi.eoss.platform.core.processing.server.orchestrator.model;

import lombok.Builder;
import lombok.Value;

/**
 * Response containing the details of a submitted job.
 */
@Value
@Builder
public class JobLaunchResponse {

    String jobId;
    String intJobId;
    String userId;
    String serviceId;
    String userUUID;

}
