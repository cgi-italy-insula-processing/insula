package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.queues.QueuesCoreBaseConfig;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@Import({QueuesCoreBaseConfig.class, AppCoreDefaultConfig.class})
@EnableConfigurationProperties(ApiClientProperties.class)
public class AppCoreConfig {

    @Value("${platform.kubernetes.api.url:}")
    private String apiUrl;

    @Value("${namespace}")
    private String namespace;

    @Value("${platform.kubernetes.event.collect.delay.millis:200}")
    private Long eventCollectDelayMillis;


    /**
     * Creates the {@link PodEventCollector} bean used to watch pod events in the configured namespace.
     *
     * @param podApiClient the Kubernetes API client used to watch pod events
     * @param podEventProcessor the processor handling collected pod events
     * @param apiClientProperties the API client properties
     * @return a configured {@link PodEventCollector} instance
     */
    @Bean
    public PodEventCollector podEventCollector(
            ApiClient podApiClient,
            KubernetesEventProcessor<Watch.Response<V1Pod>> podEventProcessor,
            ApiClientProperties apiClientProperties) {
        return new PodEventCollector(new CoreV1Api(podApiClient),
                podEventProcessor,
                namespace,
                apiClientProperties.getWatchTimeoutSeconds());
    }

    /**
     * Creates the {@link KubernetesAsyncEventCollector} bean used to asynchronously collect pod events.
     *
     * @param podEventCollector the collector used to retrieve pod events
     * @param scheduledThreadPool the executor service used to schedule the collection task
     * @return a configured {@link KubernetesAsyncEventCollector} instance for pod events
     */
    @Bean
    public KubernetesAsyncEventCollector podAsyncEventCollector(
            PodEventCollector podEventCollector,
            ScheduledExecutorService scheduledThreadPool) {
        return new KubernetesAsyncEventCollector(
                "pod",
                scheduledThreadPool,
                podEventCollector,
                eventCollectDelayMillis
        );
    }

    /**
     * Creates the {@link KubernetesAsyncEventCollector} bean used to asynchronously collect workflow events.
     *
     * @param workflowEventCollector the collector used to retrieve workflow events
     * @param scheduledThreadPool the executor service used to schedule the collection task
     * @return a configured {@link KubernetesAsyncEventCollector} instance for workflow events
     */
    @Bean
    public KubernetesAsyncEventCollector workflowAsyncEventCollector(
            WorkflowEventCollector workflowEventCollector,
            ScheduledExecutorService scheduledThreadPool) {
        return new KubernetesAsyncEventCollector(
                "workflow",
                scheduledThreadPool,
                workflowEventCollector,
                eventCollectDelayMillis
        );
    }

    /**
     * Creates the {@link WorkflowEventCollector} bean used to watch workflow events in the configured namespace.
     *
     * @param workflowApiClient the Kubernetes API client used to watch workflow events
     * @param workflowEventProcessor the processor handling collected workflow events
     * @param apiClientProperties the API client properties
     * @return a configured {@link WorkflowEventCollector} instance
     */
    @Bean
    public WorkflowEventCollector workflowEventCollector(
            ApiClient workflowApiClient,
            KubernetesEventProcessor<Watch.Response<Object>> workflowEventProcessor,
            ApiClientProperties apiClientProperties) {
        return new WorkflowEventCollector(
                new CustomObjectsApi(workflowApiClient),
                workflowEventProcessor,
                namespace,
                apiClientProperties.getWatchTimeoutSeconds()
        );
    }

    /**
     * Creates the {@link ScheduledExecutorService} bean used to schedule the asynchronous event collectors.
     *
     * @return a scheduled executor service with a fixed pool of 2 threads
     */
    @Bean
    public ScheduledExecutorService scheduledThreadPool() {
        return Executors.newScheduledThreadPool(2);
    }

    /**
     * Creates the {@link LegacyWorkflowPodEventHandler} bean used to handle legacy workflow pod events.
     *
     * @param queueService the service used to publish events to the queue
     * @return a configured {@link LegacyWorkflowPodEventHandler} instance
     */
    @Bean
    public LegacyWorkflowPodEventHandler legacyWorkflowPodEventHandler(QueueService queueService) {
        return new LegacyWorkflowPodEventHandler(queueService);
    }

    /**
     * Creates the {@link ApiClient} bean used to communicate with the Kubernetes API for workflow events.
     *
     * @param apiClientProperties the API client properties
     * @return a configured {@link ApiClient} instance
     * @throws IOException if the API client cannot be initialized
     */
    @Bean
    public ApiClient workflowApiClient(ApiClientProperties apiClientProperties) throws IOException {
        return initApiClient(apiClientProperties);
    }

    /**
     * Creates the {@link ApiClient} bean used to communicate with the Kubernetes API for pod events.
     *
     * @param apiClientProperties the API client properties
     * @return a configured {@link ApiClient} instance
     * @throws IOException if the API client cannot be initialized
     */
    @Bean
    public ApiClient podApiClient(ApiClientProperties apiClientProperties) throws IOException {
        return initApiClient(apiClientProperties);
    }

    /**
     * Creates the {@link KubernetesEventCollectorManager} bean initialized with the provided event collectors.
     *
     * @param workflowAsyncEventCollector an asynchronous collector of workflow events
     * @param podAsyncEventCollector an asynchronous collector of pod events
     * @return a configured {@link KubernetesEventCollectorManager} instance
     */
    @Bean
    public KubernetesEventCollectorManager kubernetesEventCollectorManager(
            KubernetesAsyncEventCollector workflowAsyncEventCollector,
            KubernetesAsyncEventCollector podAsyncEventCollector) {
        return new KubernetesEventCollectorManager(workflowAsyncEventCollector, podAsyncEventCollector);
    }

    /**
     * Creates the {@link EventCollectorRunner} bean used to run the configured event collectors.
     *
     * @param kubernetesEventCollectorManager the manager coordinating the event collectors
     * @return a configured {@link EventCollectorRunner} instance
     */
    @Bean
    public EventCollectorRunner eventCollectorRunner(KubernetesEventCollectorManager kubernetesEventCollectorManager) {
        return new EventCollectorRunner(kubernetesEventCollectorManager);
    }

    private ApiClient initApiClient(ApiClientProperties apiClientProperties) throws IOException {
        ApiClient apiClient = apiUrl.isEmpty()
                ? Config.fromCluster()
                : Config.fromUrl(apiUrl);

        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder()
                .readTimeout(apiClientProperties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .connectTimeout(apiClientProperties.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(apiClientProperties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .build());
        return apiClient;
    }
}