package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Class to manage Kubernetes event collectors
 *
 * @author cantaveneraf
 *
 */
@Log4j2
@RequiredArgsConstructor
public class KubernetesEventCollectorManager {

    private final KubernetesAsyncEventCollector workflowAsyncEventCollector;
    private final KubernetesAsyncEventCollector podAsyncEventCollector;

    /**
     * Start the Kubernetes event collectors
     */
    public void start() {
        LOG.info("Starting workflow event collector");
        workflowAsyncEventCollector.start();

        LOG.info("Starting pod event collector");
        podAsyncEventCollector.start();

        LOG.info("Event collectors started");
    }

}
