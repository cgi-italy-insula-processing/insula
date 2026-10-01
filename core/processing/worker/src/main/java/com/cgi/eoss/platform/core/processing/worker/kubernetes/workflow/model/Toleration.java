package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 *  Class that holds the model to create the Argo pod toleration
 *  <a href="https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/">taint-and-toleration</a>
 *
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Toleration {
    private String key;
    private String operator;
    private String value;
    private String effect;
}
