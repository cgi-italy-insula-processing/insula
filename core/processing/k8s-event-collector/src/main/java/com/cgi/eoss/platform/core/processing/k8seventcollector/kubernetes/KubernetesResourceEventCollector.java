package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

/**
 * Interface that defines the method of a collector of Kubernetes resource events
 *
 * @author cantaveneraf
 *
 */
public interface KubernetesResourceEventCollector {

    /**
     * Collect Kubernetes resource events
     *
     * @return
     *         The latest version of Kubernetes resource collected or null if the version is not available
     * @throws Exception
     *             In case of error while collecting Kubernetes resource events
     */
    String collect() throws Exception;

}
