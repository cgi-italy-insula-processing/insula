package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils;

import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.ListMeta;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.WorkflowList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1PodList;

public class ResourceBuilders {

    public static final String JOB_ID = "one";
    public static final String INT_JOB_ID = "1";
    private static final String WORKFLOW_TYPE_JOB = "job";
    private static final String RESOURCE_VERSION = "101";

    public static PodResource.PodResourceBuilder podResource(String eventType) {
        return podResource(eventType, RESOURCE_VERSION);
    }

    public static PodResource.PodResourceBuilder podResource(String eventType, String resourceVersion) {
        return PodResource.builder()
                .resourceVersion(resourceVersion)
                .isApp("true")
                .intJobId(INT_JOB_ID)
                .jobId(JOB_ID)
                .eventType(eventType);
    }

    public static V1PodList podList(String resourceVersion) {
        V1PodList podList = new V1PodList();
        V1ListMeta listMeta = new V1ListMeta();
        listMeta.setResourceVersion(resourceVersion);
        podList.setMetadata(listMeta);
        return podList;
    }

    public static WorkflowResource.WorkflowResourceBuilder workflowResource(String eventType) {
        return workflowResource(eventType, RESOURCE_VERSION);
    }

    public static WorkflowResource.WorkflowResourceBuilder workflowResource(String eventType, String resourceVersion) {
        return WorkflowResource.builder()
                .resourceVersion(resourceVersion)
                .workflowType(WORKFLOW_TYPE_JOB)
                .intJobId(INT_JOB_ID)
                .jobId(JOB_ID)
                .eventType(eventType);
    }

    public static WorkflowList workflowList(String resourceVersion) {
        WorkflowList workflowList = new WorkflowList();
        ListMeta listMeta = new ListMeta();
        listMeta.setResourceVersion(resourceVersion);
        workflowList.setMetadata(listMeta);
        return workflowList;
    }
}
