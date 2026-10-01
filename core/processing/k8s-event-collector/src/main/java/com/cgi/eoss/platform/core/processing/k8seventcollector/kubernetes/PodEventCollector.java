package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Kubernetes event collector of Pod events.
 * This implementation delegates processing of messages to a PodEventProcessor
 *
 * @author cantaveneraf
 *
 */
@Slf4j
public class PodEventCollector implements KubernetesResourceEventCollector {

    private static final Type WATCHED_TYPE = new TypeToken<Response<V1Pod>>() {
        private static final long serialVersionUID = 3503962507183013424L;
    }.getType();

    private final CoreV1Api coreV1Api;
    private final KubernetesEventProcessor<Response<V1Pod>> podEventProcessor;
    private final String namespace;
    private final int watchTimeoutSeconds;
    private String resourceVersion;

    /**
     * Initialize an instance of this class with the provided apiClient, namespace and queueService
     *
     * @param coreV1Api
     *            The client object used to interact with the Kubernetes API Server
     * @param podEventProcessor
     *            The processor of pod events
     * @param namespace
     *            The namespace of the pods to monitor
     * @param watchTimeoutSeconds The server-side timeout for the watch call
     */
    public PodEventCollector(CoreV1Api coreV1Api,
            KubernetesEventProcessor<Response<V1Pod>> podEventProcessor,
            String namespace,
            int watchTimeoutSeconds) {
        this.coreV1Api = coreV1Api;
        this.namespace = namespace;
        this.watchTimeoutSeconds = watchTimeoutSeconds;
        this.podEventProcessor = podEventProcessor;
        this.resourceVersion = null;
    }

    @Override
    public String collect() throws ApiException, IOException {
        if (Strings.isEmpty(resourceVersion)) {
            LOG.info("Pod List initial resource version is empty ({}), retrieve the latest resource version", resourceVersion);
            resourceVersion = getInitialResourceVersion();
        }

        LOG.info("Requesting Pod events from resource version '{}'", resourceVersion);

        Call call = coreV1Api.listNamespacedPodCall(namespace, null, null, null, null, null, null,
                resourceVersion, null, null, watchTimeoutSeconds, true, null);

        try (Watch<V1Pod> podWatch = Watch.createWatch(coreV1Api.getApiClient(), call, WATCHED_TYPE)) {
            for (Response<V1Pod> item : podWatch) {
                LOG.info("Got new pod event");
                if (item.object == null) {
                    LOG.warn("Ignoring pod event with null item object (pod), reset resourceVersion old resourceVersion: {}", resourceVersion);
                    resourceVersion = null;
                    continue;
                }
                podEventProcessor.process(item);
                resourceVersion = item.object.getMetadata().getResourceVersion();
                LOG.info("Handled event for Pod resource version '{}'", resourceVersion);
            }
        }

        LOG.info("Pod events handling completed, new resource version '{}'", resourceVersion);

        return resourceVersion;
    }

    private String getInitialResourceVersion() throws ApiException {
        V1PodList podList = coreV1Api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null, null, null);
        return podList.getMetadata().getResourceVersion();
    }

}
