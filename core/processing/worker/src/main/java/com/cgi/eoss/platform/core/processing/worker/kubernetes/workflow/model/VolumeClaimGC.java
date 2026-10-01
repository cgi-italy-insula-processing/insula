package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Defines the persistent volume claim garbage collection strategy of an Argo Workflow.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VolumeClaimGC {

    private String strategy;
}
