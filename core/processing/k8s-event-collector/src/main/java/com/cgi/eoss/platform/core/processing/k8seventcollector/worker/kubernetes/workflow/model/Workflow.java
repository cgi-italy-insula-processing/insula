package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

/**
 * Immutable class that models the Argo workflow
 *
 * @author cantaveneraf
 *
 */
@Value
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workflow {

    private final String apiVersion = "argoproj.io/v1alpha1";

    private final String kind = "Workflow";

    @JsonProperty("metadata")
    private final Metadata metadata;

    @JsonProperty("spec")
    private final Spec spec;

    @JsonProperty("status")
    private final Object status;

    @JsonProperty("code")
    private final Integer code;

    @JsonProperty("message")
    private final String message;

    /**
     * Get Status object representing the status of the Workflow,
     * if the status of this instance has been initialized with a Map.
     * If the status type of this instance is not a Map, this method returns null.
     *
     * @return
     *         The status of this workflow instance
     */
    public Status getStatus() {
        if (status instanceof Map<?, ?>) {
            return Status.from((Map<String, Object>) status);
        }
        return null;
    }

    /**
     * Get String object representing the status of the Workflow,
     * if the status of this instance has been initialized with a String.
     * If the status type of this instance is not a String, this method returns null.
     *
     * @return
     *         The status of this workflow instance
     */
    public String getStatusAsString() {
        if (status instanceof String) {
            return (String) status;
        }
        return null;
    }
}
