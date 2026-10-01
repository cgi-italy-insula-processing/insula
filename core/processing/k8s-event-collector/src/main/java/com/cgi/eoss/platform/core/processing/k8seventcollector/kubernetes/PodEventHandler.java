package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import io.kubernetes.client.openapi.models.V1Pod;
import static io.kubernetes.client.util.Watch.Response;

/**
 * Provides method to handle Kubernetes Pod Events by publishing messages on a queue for relevant event.
 */
public interface PodEventHandler {

    /**
     * Returns true if the Kubernetes Pod Event is supported
     * @param podEvent the Kubernetes Pod Event to verify if supported
     * @return true if supported, false otherwise
     */
    boolean supports(Response<V1Pod> podEvent);

    /**
     * Publishes messages on a queue for the input Kubernetes Pod Event if supported and relevant.
     * @param podEvent the Kubernetes Pod Event to handle
     */
    void handlePodEvent(Response<V1Pod> podEvent);
}
