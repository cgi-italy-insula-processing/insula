package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils;


import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.CorePlatformLabels;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch.Response;
import lombok.Builder;
import lombok.Value;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that models a K8s Pod Resource.
 * For test purposes only
 *
 * @author cantaveneraf
 *
 */
@Value
@Builder
public class PodResource {

    private final String resourceVersion;
    private final String isApp;
    private final String isOutputApp;
    private final String eventType;
    private final String jobId;
    private final String intJobId;
    private final String phase;
    private final String workflowStep;
    private final String initializedCondition;
    private final Integer code;
    private final String message;

    /**
     * Convert this PodResource to a mock Watch Response
     *
     * @return
     *         A mock Watch Response wrapping this object instance and its type
     */
    public Response<V1Pod> toMockWatchResponse() {
        Response<V1Pod> event = Mockito.mock(Response.class);

        WatchResponse<V1Pod> watchResponse = toWatchResponse();

        event.object = watchResponse.getObject();
        event.type = watchResponse.getType();
        return event;
    }

    /**
     * Convert this PodResource to a Watch Response
     *
     * @return
     *         A Watch Response wrapping this object instance and its type
     */
    public WatchResponse<V1Pod> toWatchResponse() {

        V1ObjectMeta podMetadata = new V1ObjectMeta();
        podMetadata.setResourceVersion(resourceVersion);

        Map<String, String> labels = new HashMap<>();

        putIfNotNull(labels, CorePlatformLabels.PLATFORM_JOB_ID_LABEL, jobId);
        putIfNotNull(labels, CorePlatformLabels.PLATFORM_INT_JOB_ID_LABEL, intJobId);
        putIfNotNull(labels, CorePlatformLabels.PLATFORM_WORKFLOW_STEP_LABEL, workflowStep);

        podMetadata.setLabels(labels);

        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase(phase);

        if (initializedCondition != null) {
            V1PodCondition condition = new V1PodCondition();
            condition.setType("Initialized");
            condition.setStatus(initializedCondition);
            podStatus.addConditionsItem(condition);
        }

        V1Pod pod = new V1Pod();
        pod.setApiVersion("v1");
        pod.setKind("Pod");
        pod.setStatus(podStatus);
        pod.setMetadata(podMetadata);
        WatchResponse<V1Pod> podWatch = new WatchResponse<>();
        podWatch.setType(eventType);
        podWatch.setObject(pod);
        return podWatch;
    }

    private static <K, V> void putIfNotNull(Map<K, V> map, K key, V value) {
        if (key != null && value != null) {
            map.put(key, value);
        }
    }

}