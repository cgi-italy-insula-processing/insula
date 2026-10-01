package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.INT_JOB_ID;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.JOB_ID;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class LegacyWorkflowPodEventHandlerTest {

    private LegacyWorkflowPodEventHandler legacyWorkflowPodEventHandler;

    private QueueService queueService;

    @Before
    public void setUp(){
        queueService = mock(QueueService.class);
        legacyWorkflowPodEventHandler = new LegacyWorkflowPodEventHandler(queueService);
    }

    @After
    public void shutDown() {
        Mockito.verifyNoMoreInteractions(queueService);
    }

    @Test
    public void testSupports_ReturnsTrue_WhenModifiedPodEventHasWorkflowStepLabel() {
        assertThat(legacyWorkflowPodEventHandler.supports(
                podResource("MODIFIED")
                        .workflowStep("someWorkflowStep")
                        .build()
                        .toMockWatchResponse())
        ).isTrue();

    }

    @Test
    public void testSupports_ReturnsFalse_WhenModifiedPodEventHasNoWorkflowStepLabel() {
        assertThat(legacyWorkflowPodEventHandler.supports(
                podResource("MODIFIED")
                        .build()
                        .toMockWatchResponse())
        ).isFalse();
    }

    @Test
    public void testHandlePodEvent_DispatchesWorkflowRunningEventMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInPendingStatusAndInitializedConditionIsTrue() {

        InOrder inOrder = Mockito.inOrder(queueService);

        Watch.Response<V1Pod> event = podResource("MODIFIED")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Pending")
                .initializedCondition("True")
                .build()
                .toMockWatchResponse();

        legacyWorkflowPodEventHandler.handlePodEvent(event);

        inOrder.verify(queueService).sendObject(
                ProcessingCoreQueueNames.KUBERNETES_EVENTS,
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_RUNNING)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build());

    }

    @Test
    public void testProcess_DoesNotDispatchWorkflowRunningEventMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInPendingStatusAndInitializedConditionIsFalse() throws Exception {

        Watch.Response<V1Pod> event = podResource("MODIFIED")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Pending")
                .initializedCondition("False")
                .build()
                .toMockWatchResponse();

        legacyWorkflowPodEventHandler.handlePodEvent(event);

    }

    @Test
    public void testProcess_DoesNotDispatchWorkflowRunningEventMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInPendingStatusWithoutConditions() {

        Watch.Response<V1Pod> event = podResource("MODIFIED")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Pending")
                .build()
                .toMockWatchResponse();

        legacyWorkflowPodEventHandler.handlePodEvent(event);

    }

    @Test
    public void testProcess_DoesNotDispatchWorkflowRunningEventMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInRunningStatus() throws Exception {

        Watch.Response<V1Pod> event = podResource("MODIFIED")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Running")
                .build()
                .toMockWatchResponse();

        legacyWorkflowPodEventHandler.handlePodEvent(event);

    }

    @Test
    public void testProcess_DoesNotDispatchWorkflowRunningEventMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInSucceededStatus() throws Exception {

        Watch.Response<V1Pod> event = podResource("MODIFIED")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Succeeded")
                .build()
                .toMockWatchResponse();

        legacyWorkflowPodEventHandler.handlePodEvent(event);

    }

}