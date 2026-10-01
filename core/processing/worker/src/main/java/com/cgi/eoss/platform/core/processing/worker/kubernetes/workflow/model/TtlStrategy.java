package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Defines the retention periods of an Argo Workflow after reaching a terminal state.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TtlStrategy {

    private Integer secondsAfterFailure;

    private Integer secondsAfterCompletion;
}
