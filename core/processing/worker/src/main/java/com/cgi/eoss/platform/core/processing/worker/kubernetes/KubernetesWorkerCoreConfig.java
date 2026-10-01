package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service.KubernetesWorkerJobDataService;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Configuration of the worker core application for the deployment on Kubernetes
 */
@Configuration
public class KubernetesWorkerCoreConfig {

    /**
     * Creates the workerId bean
     *
     * @param workerId the ID of the current worker application
     * @return the workerId bean
     */
    @Bean
    public String workerId(@Value("${platform.worker.workerId:workerId}") String workerId) {
        return workerId;
    }

    /**
     * Creates the JobUpdateListener bean with the provided dependencies
     *
     * @param queueService the service to send and receive messages to/from queues
     * @param workerId the ID of the current worker
     * @return the JobUpdateListener bean
     */
    @Bean
    public PlatformWorkerUpdateManager jobUpdateListener(QueueService queueService, @Qualifier("workerId") String workerId) {
        return new PlatformWorkerUpdateManager(queueService, workerId);
    }

    @Bean
    public CustomObjectsApi workflowCustomObjectsApi(ApiClient workflowApiClient) {
        return new CustomObjectsApi(workflowApiClient);
    }

    @Bean
    public ApiClient workflowApiClient(@Value("${platform.kubernetes.workflow.api.client.read.timeout.seconds:0}") Integer apiClientReadTimeoutSeconds) throws IOException {
        ApiClient apiClient = Config.fromCluster();
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder()
            .readTimeout(apiClientReadTimeoutSeconds, TimeUnit.SECONDS)
            .build());
        return apiClient;
    }

    @Bean
    public CoreV1Api workflowCoreV1Api(ApiClient workflowApiClient) {
        return new CoreV1Api(workflowApiClient);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public BatchV1Api batchApi(ApiClient workflowApiClient) {
        return new BatchV1Api(workflowApiClient);
    }

    @Bean
    public WorkflowService workflowService(
        LegacyWorkflowProperties legacyWorkflowProperties,
        LegacyWorkflowBuilder legacyWorkflowBuilder,
        CoreV1Api workflowCoreV1Api,
        CustomObjectsApi workflowCustomObjectsApi,
        ObjectMapper objectMapper) {

        return new WorkflowService(workflowCoreV1Api, workflowCustomObjectsApi, legacyWorkflowProperties.getNamespace(), legacyWorkflowBuilder, objectMapper);
    }

    /**
     * Creates the kubernetesWorkerJobUpdatesManager bean with the provided dependencies
     *
     * @param workflowService high level service to manage argo workflows
     * @param jobUpdateListener the service to send job updates events
     * @param kubernetesWorkerJobDataService service to manage kubernetes workerjob persistence
     * @param deleteOnFailure flag to determine whether a workflow should be deleted on failure
     * @return the kubernetesWorkerJobUpdatesManager bean
     */
    @Bean
    public KubernetesWorkerJobUpdatesManagerImpl kubernetesWorkerJobUpdatesManager(WorkflowService workflowService,
                                                                                   JobUpdateListener jobUpdateListener,
                                                                                   KubernetesWorkerJobDataService kubernetesWorkerJobDataService,
                                                                                   @Value("${platform.worker.workflow.legacy.deleteOnFailure:true}") boolean deleteOnFailure) {
        return new KubernetesWorkerJobUpdatesManagerImpl(workflowService, jobUpdateListener,
            kubernetesWorkerJobDataService, deleteOnFailure);
    }

    /**
     * Creates the dispatcher responsible for workflow events.
     *
     * @param kubernetesWorkerJobUpdatesManager manager handling status updates for legacy Kubernetes worker jobs
     * @return a dispatcher that routes legacy workflow events to the Kubernetes worker job updates manager
     */
    @Bean
    public LegacyWorkflowEventsDispatcher legacyWorkflowEventsDispatcher(KubernetesWorkerJobUpdatesManagerImpl kubernetesWorkerJobUpdatesManager){
        return new LegacyWorkflowEventsDispatcher(kubernetesWorkerJobUpdatesManager);
    }

}
