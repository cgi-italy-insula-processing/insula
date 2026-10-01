package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.INT_JOB_ID;import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.JOB_ID;import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.workflowList;import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.workflowResource;import static com.cgi.eoss.platform.testutils.json.JsonUtils.serialize;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.TimeUnit;

import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.WatchResponse;import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;


import io.fabric8.mockwebserver.DefaultMockServer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.Config;

public class WorkflowEventCollectorTest {

    private DefaultMockServer webServer;

    private QueueService queueService;

    private WorkflowEventCollector eventCollector;

    private InOrder inOrder;

    @Before
    public void setUp() {

        queueService = mock(QueueService.class);
        inOrder = inOrder(queueService);

        webServer = new DefaultMockServer();
        webServer.start();

        String k8Url = webServer.url("/k8").toString().replace("localhost", "127.0.0.1");

        ApiClient apiClient = Config.fromUrl(k8Url);
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder()
                .readTimeout(1, TimeUnit.SECONDS)
                .build());
        eventCollector = new WorkflowEventCollector(
                new CustomObjectsApi(apiClient),
                new WorkflowEventProcessor(queueService),
                "ns-platform",
                30);
    }

    @After
    public void shutdown() throws Exception {
        webServer.shutdown();
    }

    @Test
    public void testDispatchMessageForModifiedWorkflowEventInSucceededPhase() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Succeeded")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));


        inOrder.verify(queueService, times(1)).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_COMPLETED)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build());

        verify(queueService, times(1)).sendObject(any(), any());

    }

    @Test
    public void testCollect_DoesNotDispatchMessage_WhenWorkflowModifiedEventIsInRunningPhase() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Running")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));

        verifyNoInteractions(queueService);
    }

    @Test
    public void testDispatchMessageForModifiedWorkflowEventInFailedPhase() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Failed")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));


        inOrder.verify(queueService, times(1)).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_FAILED)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build());
        verify(queueService, times(1)).sendObject(any(), any());
    }

    @Test
    public void testRequestWorkflowEventsFromTheLatestResourceVersion() throws Exception {

        {
            webServer.expect()
                    .get()
                    .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                    .andReturn(200, workflowList("100"))
                    .once();

            WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                    .phase("Succeeded")
                    .build().toWatchResponse();

            webServer.expect()
                    .get()
                    .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                    .andReturn(200, watchResponse)
                    .once();

            assertThat(eventCollector.collect(), is("101"));


            inOrder.verify(queueService, times(1)).sendObject(
                    ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                    K8SEvent.newBuilder()
                            .setEventType(K8SEventType.K8S_WORKFLOW_COMPLETED)
                            .setJobId(JOB_ID)
                            .setIntJobId(INT_JOB_ID).build());
        }

        WatchResponse<Workflow> watchResponseTwo = workflowResource("MODIFIED", "102")
                .phase("Failed")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=101&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponseTwo)
                .once();

        assertThat(eventCollector.collect(), is("102"));


        inOrder.verify(queueService, times(1)).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_FAILED)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build());

        verify(queueService, times(2)).sendObject(any(), any());
    }

    @Test
    public void testDoNotSendMessageForWorkflowEventWithoutJobId() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Running")
                .jobId(null)
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));

        verify(queueService, times(0)).sendObject(any(), any());
    }

    @Test
    public void testDoNotSendMessageForWorkflowEventWithoutIntJobId() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Running")
                .intJobId(null)
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));

        verify(queueService, times(0)).sendObject(any(), any());
    }

    @Test
    public void testDoNotDispatchMessageForModifiedWorkflowEventInUnknownPhase() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Unknown")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));

        verify(queueService, times(0)).sendObject(any(), any());

    }

    @Test
    public void testDispatchMultipleMessagesWhenWatchReturnsMultipleEvents() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        String watchResponseOne = serialize(
                workflowResource("MODIFIED", "101")
                        .phase("Succeeded")
                        .build().toWatchResponse());

        String watchResponseTwo = serialize(
                workflowResource("MODIFIED", "102")
                        .phase("Failed")
                        .build().toWatchResponse());

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponseOne + System.lineSeparator() + watchResponseTwo)
                .once();

        assertThat(eventCollector.collect(), is("102"));


        inOrder.verify(queueService, times(1)).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_COMPLETED)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build());


        inOrder.verify(queueService, times(1)).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_FAILED)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build());

        verify(queueService, times(2)).sendObject(any(), any());
    }

    @Test
    public void testDispatchMessageForDeletedWorkflowEvent() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("DELETED", "101")
                .phase("ResourceStatus")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));


        inOrder.verify(queueService, times(1)).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_DELETED)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID)
                        .setResourceStatus("ResourceStatus")
                        .build());
        verify(queueService, times(1)).sendObject(any(), any());
    }

    @Test
    public void testDoNotDispatchMessageForUnkownWorkflowEvent() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("UNKNOWN", "101")
                .phase("ResourceStatus")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));

        verify(queueService, times(0)).sendObject(any(), any());
    }

    @Test
    public void testDoNotDispatchMessageForWorkflowEventOfUnknownWorkflowType() throws Exception {

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?watch=false")
                .andReturn(200, workflowList("100"))
                .once();

        WatchResponse<Workflow> watchResponse = workflowResource("MODIFIED", "101")
                .phase("Failed")
                .workflowType("unknown")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/ns-platform/workflows?resourceVersion=100&timeoutSeconds=30&watch=true")
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect(), is("101"));

        verify(queueService, times(0)).sendObject(any(), any());
    }

}