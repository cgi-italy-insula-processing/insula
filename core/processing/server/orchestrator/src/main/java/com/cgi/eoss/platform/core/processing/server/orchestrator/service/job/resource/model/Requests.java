package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the requested resources for a job.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Requests {

    private String ram;
    private String cpu;
}