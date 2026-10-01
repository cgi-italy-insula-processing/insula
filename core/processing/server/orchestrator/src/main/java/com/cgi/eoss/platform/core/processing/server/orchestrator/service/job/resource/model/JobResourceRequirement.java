package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the resource requirements for a job, such as storage and other
 * computer-related resources.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResourceRequirement {

    private Integer storage;
    private Integer gpus;
    private Requests requests;
    private Limits limits;
}