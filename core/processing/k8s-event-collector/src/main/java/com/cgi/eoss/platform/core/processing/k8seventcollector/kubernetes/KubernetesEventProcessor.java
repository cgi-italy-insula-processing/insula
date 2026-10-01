package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

/**
 * Interface that defines the method of a processor of Kubernetes resource events
 *
 * @author cantaveneraf
 *
 * @param <T>
 *            The Kubernetes resource event type
 */
public interface KubernetesEventProcessor<T> {

    /**
     * Process Kubernetes resource events
     *
     * @param event
     *            The event to process
     */
    void process(T event);

}
