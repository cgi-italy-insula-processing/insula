package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the upper bounds for job resource usage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Limits {

    private String ram;
    private String cpu;
}