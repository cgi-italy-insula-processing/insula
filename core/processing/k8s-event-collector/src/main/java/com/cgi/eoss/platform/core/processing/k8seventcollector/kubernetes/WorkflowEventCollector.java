package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import java.io.IOException;
import java.lang.reflect.Type;

import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.WorkflowList;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.utils.WorkflowUtils;
import org.apache.logging.log4j.util.Strings;


import com.google.common.reflect.TypeToken;
import okhttp3.Call;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * Kubernetes event collector of Workflow events.
 * This implementation publishes messages on a queue for relevant events.
 *
 * @author cantaveneraf
 *
 */
@Slf4j
public class WorkflowEventCollector implements KubernetesResourceEventCollector {

    private static final Type WATCHED_TYPE = new TypeToken<Response<Object>>() {
        private static final long serialVersionUID = 944095197918178019L;
    }.getType();

    private final CustomObjectsApi customObjectsApi;
    private final KubernetesEventProcessor<Response<Object>> workflowEventProcessor;
    private final String namespace;
    private final int watchTimeoutSeconds;
    private String resourceVersion;

    /**
     * Initialize an instance of this class with the provided apiClient, namespace and queueService
     *
     * @param customObjectsApi
     *            The client object used to interact with the Kubernetes API Server
     * @param workflowEventProcessor
     *            The processor of Argo Workflow Kubernetes events
     * @param namespace
     *            The namespace of the workflows to monitor
     * @param watchTimeoutSeconds The server-side timeout in seconds for the watch call
     */
    public WorkflowEventCollector(CustomObjectsApi customObjectsApi,
            KubernetesEventProcessor<Response<Object>> workflowEventProcessor,
            String namespace,
                                  int watchTimeoutSeconds) { // , ResourceRepository resourceRepository
        this.customObjectsApi = customObjectsApi;
        this.namespace = namespace;
        this.watchTimeoutSeconds = watchTimeoutSeconds;
        this.workflowEventProcessor = workflowEventProcessor;
        this.resourceVersion = null;
    }

    @Override
    public String collect() throws ApiException, IOException {
        if (Strings.isEmpty(resourceVersion)) {
            LOG.info("Workflow List initial resource version is empty ({}), retrieve the latest resource version", resourceVersion);
            resourceVersion = getInitialResourceVersion();
        }

        LOG.info("Requesting Workflow events from resource version '{}'", resourceVersion);

        Call call = customObjectsApi.listNamespacedCustomObjectCall("argoproj.io", "v1alpha1",
                namespace, "workflows", null, null, null, null, null, null, resourceVersion, null, watchTimeoutSeconds, true, null);

        try (Watch<Object> workflowWatch = Watch.createWatch(customObjectsApi.getApiClient(), call, WATCHED_TYPE)) {
            for (Response<Object> item : workflowWatch) {
                LOG.info("Got new workflow event");
                Workflow workflow = WorkflowUtils.deserializeWorkflow(item.object);
                workflowEventProcessor.process(item);
                resourceVersion = WorkflowUtils.nextResourceVersion(workflow);
                LOG.info("Handled event for Workflow resource version '{}'", resourceVersion);
            }
        }

        LOG.info("Workflow events handling completed, new resource version '{}'", resourceVersion);

        return resourceVersion;
    }

    private String getInitialResourceVersion() throws ApiException {
        Object object = customObjectsApi.listNamespacedCustomObject("argoproj.io", "v1alpha1",
                namespace, "workflows", null, null, null, null, null, null, null, null, null, false);
        WorkflowList workflowList = WorkflowUtils.deserializeWorkflowList(object);
        return workflowList.getMetadata().getResourceVersion();
    }

}
