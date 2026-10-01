package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import java.nio.file.Path;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a job output that has been repatriated from the object storage into the platform output product storage.
 * It pairs the service output identifier with the local path(s) the output files were written to.
 */
@Getter
@AllArgsConstructor
public final class RepatriatedOutput {

    private final String outputId;

    private final List<Path> repatriatedPaths;
}
