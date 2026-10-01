package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.processing.k8seventcollector.AppCoreTestConfig;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;


import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.INT_JOB_ID;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.JOB_ID;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podResource;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { AppCoreTestConfig.class, AppCoreConfig.class })
@TestPropertySource(locations = { "classpath:test-k8-event-collector-core.properties"})
public class PodEventProcessorIT {

    @Autowired
    private PodEventProcessor podEventProcessor;

    @Autowired
    private QueueService queueService;

    @Before
    public void setUp() {
        initKubernetesEventsQueue();
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);
    }

    @After
    public void shutDown() {
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);
    }

    @Test
    public void testProcess_DispatchesWorkflowRunningMessage_WhenModifiedPodEventHasWorkflowStepLabelAndIsPendingAndInitialized() {


        Watch.Response<V1Pod> event = podResource("MODIFIED")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Pending")
                .initializedCondition("True")
                .build()
                .toMockWatchResponse();

        podEventProcessor.process(event);

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(1);

        Message message = queueService.receive(ProcessingCoreQueueNames.KUBERNETES_EVENTS);
        assertThat(message.getHeaders()).isEmpty();

        assertThat((K8SEvent) message.getPayload()).isEqualTo(
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_RUNNING)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build()
        );

    }

    private void initKubernetesEventsQueue() {
        queueService.sendObject(ProcessingCoreQueueNames.KUBERNETES_EVENTS, "test-message");
        Message message = queueService.receive(ProcessingCoreQueueNames.KUBERNETES_EVENTS);
        assertThat(message.getPayload()).isEqualTo("test-message");
    }

}
