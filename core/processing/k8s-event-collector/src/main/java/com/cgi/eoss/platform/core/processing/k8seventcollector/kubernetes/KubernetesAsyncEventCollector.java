package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class to asynchronously collect Kubernetes events.
 * Events will be continuously collected until the collector is explicitly stopped.
 *
 * @author cantaveneraf
 *
 */
@Log4j2
public class KubernetesAsyncEventCollector {

    private final String resourceType;
    private final AtomicBoolean stopped;
    private final KubernetesResourceEventCollector eventCollector;
    private final ScheduledExecutorService executor;
    private final long collectDelay;
    private ScheduledFuture<?> pendingTaskResult;

    /**
     * Initialize an instance of this class for the event collector of a
     * the given resource type.
     *
     * @param resourceType
     *            A string to identify the type of resource for which events will be collected.
     *            Used for logging purposes only.
     * @param executor
     *            The executor to schedule the periodic, asynchronous collection of Kubernetes resource events
     * @param eventCollector
     *            The synchronous Kubernetes event collector
     * @param collectDelayMillis
     *            The delay, in milliseconds, between calls to the synchronous Kubernetes event collector
     */
    public KubernetesAsyncEventCollector(
            String resourceType,
            ScheduledExecutorService executor,
            KubernetesResourceEventCollector eventCollector,
            long collectDelayMillis) {
        this.resourceType = resourceType;
        this.eventCollector = eventCollector;
        this.executor = executor;
        this.stopped = new AtomicBoolean(false);
        this.collectDelay = collectDelayMillis;
    }

    /**
     * Start the asynchronous event collector.
     *
     * @throws IllegalStateException
     *             If the collector was already started.
     */
    public synchronized void start() {

        if (pendingTaskResult != null) {
            throw new IllegalStateException("Can't start event collector for resource " + resourceType + ": already started");
        }

        stopped.set(false);
        LOG.info("Starting async event collector of resource {}", resourceType);
        final int initialCollectDelay = 0;
        pendingTaskResult = executor.scheduleWithFixedDelay(this::collectSafe, initialCollectDelay, collectDelay, TimeUnit.MILLISECONDS);

    }

    /**
     * Stop the asynchronous event collector
     *
     * @throws IllegalStateException
     *             If the collector was not started.
     */
    public synchronized void stop() {

        if (pendingTaskResult == null) {
            throw new IllegalStateException("Can't stop event collector for resource " + resourceType + ": was not started");
        }

        LOG.info("Stopping event collector of resource {}", resourceType);
        final boolean mayInterruptIfRunning = false;
        pendingTaskResult.cancel(mayInterruptIfRunning);
        pendingTaskResult = null;
        stopped.set(true);
    }

    /**
     * Check if the event collector is stopped
     *
     * @return
     *         true if the event collector was stopped, false otherwise
     */
    public synchronized boolean isStopped() {
        return stopped.get();
    }

    private void collectSafe() {
        try {
            eventCollector.collect();
        } catch (Exception e) {
            LOG.warn("Collect event failure for resource {}", resourceType, e);
        }
    }
}
