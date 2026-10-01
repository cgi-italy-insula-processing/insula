package com.cgi.eoss.platform.core.processing.server.orchestrator.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Request to submit a job.
 */
@Value
@Builder
public class JobLaunchRequest {

    String jobId;
    String userId;
    String serviceId;
    String jobConfigLabel;
    String jobParent;
    List<Param> inputList;

    /**
     * Represents param of a job.
     * */
    @Value
    @Builder
    public static class Param {

        String name;
        List<String> value;
        String type;
    }
}