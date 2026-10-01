package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.K8SEvent;

/**
 * Handles a {@link K8SEvent} notifying a lifecycle change of a Kubernetes resource managed by the
 * worker.
 */
public interface KubernetesEventDispatcher {

    /**
     * Handles the provided event when its type belongs to the category of events managed by this
     * dispatcher.
     *
     * @param event
     *            the Kubernetes lifecycle event to be handled
     * @return
     *            {@code true} if this dispatcher recognised the event type and handled the event;
     *            {@code false} if the event type is not handled by this dispatcher
     */
    boolean dispatch(K8SEvent event);
}
