package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents request and limit values for a single resource dimension.
 * Example: RAM in MiB
 */
@Getter
@Builder
public class ResourceBounds {

    private final String request;
    private final String limit;

}