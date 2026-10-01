package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils;

import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.CorePlatformLabels;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Metadata;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Spec;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Workflow;
import io.kubernetes.client.util.Watch.Response;
import lombok.Builder;
import lombok.Value;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that models a K8s Workflow Resource.
 * For test purposes only
 *
 * @author cantaveneraf
 *
 */
@Value
@Builder
public class WorkflowResource {

    @Value
    @Builder
    public static class WorkflowStatus {
        private final String phase;
    }

    private final String resourceVersion;
    private final String workflowType;
    private final String eventType;
    private final String jobId;
    private final String intJobId;
    private final String phase;

    private final Integer code;
    private final String message;
    private final Spec spec;

    /**
     * Convert this WorkflowResource to a mock Watch Response
     *
     * @return
     *         A mock Watch Response wrapping this object instance and its type
     */
    public Response<Object> toMockWatchResponse() {
        Response<Object> event = Mockito.mock(Response.class);

        WatchResponse<Workflow> watchResponse = toWatchResponse();

        event.object = watchResponse.getObject();
        event.type = watchResponse.getType();
        return event;
    }

    /**
     * Convert this WorkflowResource to a Watch Response
     *
     * @return
     *         A Watch Response wrapping this object instance and its type
     */
    public WatchResponse<Workflow> toWatchResponse() {

        Metadata workflowMetadata = new Metadata();
        workflowMetadata.setResourceVersion(resourceVersion);

        Map<String, String> labels = new HashMap<>();
        putIfNotNull(labels, CorePlatformLabels.PLATFORM_WORKFLOW_TYPE_LABEL, workflowType);
        putIfNotNull(labels, CorePlatformLabels.PLATFORM_JOB_ID_LABEL, jobId);
        putIfNotNull(labels, CorePlatformLabels.PLATFORM_INT_JOB_ID_LABEL, intJobId);

        workflowMetadata.setLabels(labels);

        Map<String, Object> workflowStatus = new HashMap<>();
        putIfNotNull(workflowStatus, "phase", phase);

        Workflow workflow = new Workflow(workflowMetadata, spec, workflowStatus, code, message);

        WatchResponse<Workflow> workflowWatch = new WatchResponse<>();
        workflowWatch.setType(eventType);
        workflowWatch.setObject(workflow);
        return workflowWatch;
    }

    private static <K, V> void putIfNotNull(Map<K, V> map, K key, V value) {
        if (key != null && value != null) {
            map.put(key, value);
        }
    }
}
