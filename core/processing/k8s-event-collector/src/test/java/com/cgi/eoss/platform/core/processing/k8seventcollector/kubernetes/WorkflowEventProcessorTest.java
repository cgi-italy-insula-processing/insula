package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import io.kubernetes.client.util.Watch.Response;
import org.junit.After;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class WorkflowEventProcessorTest {

    private final QueueService queueService = mock(QueueService.class);

    private final WorkflowEventProcessor eventProcessor = new WorkflowEventProcessor(queueService);

    private final InOrder inOrder = inOrder(queueService);

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDispatchMessageForModifiedWorkflowEventInSucceededPhase() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase("Succeeded")
                    .build().toMockWatchResponse();

        eventProcessor.process(event);


        inOrder.verify(queueService).sendObject(
                    ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                    K8SEvent.newBuilder()
                                .setEventType(K8SEventType.K8S_WORKFLOW_COMPLETED)
                                .setJobId(ResourceBuilders.JOB_ID)
                                .setIntJobId(ResourceBuilders.INT_JOB_ID).build());

    }

    @Test
    public void testProcess_DoesNotDispatchAnyMessage_WhenWorkflowModifiedEventIsInRunningPhase() throws Exception {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                .phase("Running")
                .build().toMockWatchResponse();

        eventProcessor.process(event);

        // The verifyNoMoreInteractions in @After asserts no sendObject was called.
    }

    @Test
    public void testDispatchMessageForModifiedWorkflowEventInFailedPhase() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase("Failed")
                    .build().toMockWatchResponse();

        eventProcessor.process(event);


        inOrder.verify(queueService).sendObject(
                    ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                    K8SEvent.newBuilder()
                                .setEventType(K8SEventType.K8S_WORKFLOW_FAILED)
                                .setJobId(ResourceBuilders.JOB_ID)
                                .setIntJobId(ResourceBuilders.INT_JOB_ID).build());

    }

    @Test
    public void testDoNotSendMessageForWorkflowEventWithoutJobId() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase("Running")
                    .jobId(null)
                    .build().toMockWatchResponse();

        eventProcessor.process(event);

    }

    @Test
    public void testDoNotSendMessageForWorkflowEventWithoutIntJobId() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase("Running")
                    .intJobId(null)
                    .build().toMockWatchResponse();

        eventProcessor.process(event);

    }

    @Test
    public void testDoNotDispatchMessageForModifiedWorkflowEventInUnknownPhase() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase("Unknown")
                    .build().toMockWatchResponse();

        eventProcessor.process(event);

    }

    @Test
    public void testDoNotDispatchMessageForModifiedWorkflowEventWhenPhaseIsNotSet() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase(null)
                    .build().toMockWatchResponse();

        eventProcessor.process(event);

    }

    @Test
    public void testDispatchMessageForDeletedWorkflowEvent() {

        Response<Object> event = ResourceBuilders.workflowResource("DELETED")
                    .phase("ResourceStatus")
                    .build().toMockWatchResponse();

        eventProcessor.process(event);


        inOrder.verify(queueService).sendObject(
                    ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                    K8SEvent.newBuilder()
                                .setEventType(K8SEventType.K8S_WORKFLOW_DELETED)
                                .setJobId(ResourceBuilders.JOB_ID)
                                .setIntJobId(ResourceBuilders.INT_JOB_ID)
                                .setResourceStatus("ResourceStatus")
                                .build());

    }

    @Test
    public void testDoNotDispatchMessageForUnkownWorkflowEvent() {

        Response<Object> event = ResourceBuilders.workflowResource("UNKNOWN")
                    .phase("ResourceStatus")
                    .build().toMockWatchResponse();

        eventProcessor.process(event);

    }

    @Test
    public void testDoNotDispatchMessageForWorkflowEventOfUnknownWorkflowType() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                    .phase("Failed")
                    .workflowType("unknown")
                    .build().toMockWatchResponse();

        eventProcessor.process(event);

    }

    @Test
    public void testDoNotThrowWhenMessageProcessingFails() {

        Response<Object> event = ResourceBuilders.workflowResource("MODIFIED")
                .phase("Succeeded")
                .build().toMockWatchResponse();

        K8SEvent k8sEvent = K8SEvent.newBuilder()
                .setEventType(K8SEventType.K8S_WORKFLOW_COMPLETED)
                .setJobId(ResourceBuilders.JOB_ID)
                .setIntJobId(ResourceBuilders.INT_JOB_ID).build();

        doThrow(IllegalStateException.class).when(queueService).sendObject(ProcessingCoreQueueNames.KUBERNETES_EVENTS, k8sEvent);

        eventProcessor.process(event);

        inOrder.verify(queueService).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                k8sEvent);

    }

}