package com.cgi.eoss.platform.core.processing.worker.kubernetes.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * DTO class modeling a workflow status info
 */
@Data
@NoArgsConstructor
public class WorkflowInfo {
    private Instant startedAt;
    private Instant finishedAt;
    private String status;
}
