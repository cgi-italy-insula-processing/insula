package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.processing.k8seventcollector.AppCoreTestConfig;
import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.WatchResponse;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.INT_JOB_ID;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.JOB_ID;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podList;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podResource;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { AppCoreTestConfig.class, AppCoreConfig.class })
@TestPropertySource(locations = { "classpath:test-k8-event-collector-core.properties"})
public class PodEventCollectorIT {

    private static final String REQUEST_PATH_INITIAL_RESOURCE_VERSION = "/k8/api/v1/namespaces/ns-platform/pods";
    private static final String INITIAL_RESOURCE_VERSION = "100";
    private static final String WATCH_REQUEST_PATH = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion=" + INITIAL_RESOURCE_VERSION + "&timeoutSeconds=30&watch=true";
    private DefaultMockServer webServer;

    @Autowired
    private QueueService queueService;

    private PodEventCollector eventCollector;

    @Autowired
    private PodEventProcessor podEventProcessor;

    @Before
    public void setUp() {
        webServer = new DefaultMockServer();
        webServer.start();

        ApiClient podApiClient = getPodApiClient();
        eventCollector = new PodEventCollector(
                new CoreV1Api(podApiClient),
                podEventProcessor,
                "ns-platform",
                30);

        mockInitialResourceVersionRequest();
        initQueue();
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);

    }

    @After
    public void shutdown() {

        webServer.shutdown();
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);
    }


    @Test
    public void testCollect_DispatchesWorkflowRunningEventMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInPendingStatusAndInitialized() throws Exception {

        mockInitialResourceVersionRequest();

        WatchResponse<V1Pod> watchResponse = podResource("MODIFIED", "101")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Pending")
                .initializedCondition("True")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath(WATCH_REQUEST_PATH)
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect()).isEqualTo("101");
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(1);

        Message message = queueService.receive(ProcessingCoreQueueNames.KUBERNETES_EVENTS);
        assertThat(message.getHeaders()).isEmpty();

        assertThat((K8SEvent) message.getPayload()).isEqualTo(
                K8SEvent.newBuilder()
                        .setEventType(K8SEventType.K8S_WORKFLOW_RUNNING)
                        .setJobId(JOB_ID)
                        .setIntJobId(INT_JOB_ID).build()
        );

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(WATCH_REQUEST_PATH);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }

    }

    @Test
    public void testCollect_DoesNotDispatchMessage_WhenModifiedPodEventHasWorkflowStepProcessingLabelAndIsInPendingStatusAndNotInitialized() throws Exception {

        mockInitialResourceVersionRequest();

        WatchResponse<V1Pod> watchResponse = podResource("MODIFIED", "101")
                .isApp("false")
                .workflowStep(CorePlatformLabels.WORKFLOW_STEP_PROCESSING_VALUE)
                .phase("Pending")
                .initializedCondition("False")
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath(WATCH_REQUEST_PATH)
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect()).isEqualTo("101");
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(WATCH_REQUEST_PATH);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    @Test
    public void testCollect_DoesNotDispatchMessage_WhenModifiedPodEventWithOutputAppLabelIsWithoutPhase() throws Exception {

        mockInitialResourceVersionRequest();

        WatchResponse<V1Pod> watchResponse = podResource("MODIFIED", "101")
                .isApp("false")
                .isOutputApp("true")
                .phase(null)
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath(WATCH_REQUEST_PATH)
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect()).isEqualTo("101");
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(WATCH_REQUEST_PATH);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }

    }

    @Test
    public void testCollect_DoesNotDispatchMessage_WhenModifiedPodEventWithAppLabelIsWithoutPhase() throws Exception {

        mockInitialResourceVersionRequest();

        WatchResponse<V1Pod> watchResponse = podResource("MODIFIED", "101")
                .phase(null)
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath(WATCH_REQUEST_PATH)
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect()).isEqualTo("101");
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(WATCH_REQUEST_PATH);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }

    }

    @Test
    public void testCollect_DoesNotDispatchMessage_WhenPodEventTypeIsUnknown() throws Exception {

        mockInitialResourceVersionRequest();

        WatchResponse<V1Pod> watchResponse = podResource("UNKNOWN", "101")
                .phase("ResourceStatus")
                .build().toWatchResponse();

        String watchRequestPath = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion=" + INITIAL_RESOURCE_VERSION + "&timeoutSeconds=30&watch=true";
        webServer.expect()
                .get()
                .withPath(watchRequestPath)
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect()).isEqualTo("101");
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(WATCH_REQUEST_PATH);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    @Test
    public void testCollect_DoesNotDispatchMessage_WhenPodEventIsOfUnknownPodType() throws Exception {

        mockInitialResourceVersionRequest();

        WatchResponse<V1Pod> watchResponse = podResource("MODIFIED", "101")
                .phase("Failed")
                .isApp(null)
                .isOutputApp(null)
                .build().toWatchResponse();

        webServer.expect()
                .get()
                .withPath(WATCH_REQUEST_PATH)
                .andReturn(200, watchResponse)
                .once();

        assertThat(eventCollector.collect()).isEqualTo("101");
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.KUBERNETES_EVENTS)).isEqualTo(0);

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(WATCH_REQUEST_PATH);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    private ApiClient getPodApiClient() {
        String k8Url = webServer.url("/k8").replace("localhost", "127.0.0.1");

        ApiClient podApiClient = Config.fromUrl(k8Url);
        podApiClient.setHttpClient(podApiClient.getHttpClient().newBuilder()
                .readTimeout(3, TimeUnit.SECONDS)
                .build());
        return podApiClient;
    }

    private void mockInitialResourceVersionRequest() {
        webServer.expect()
                .get()
                .withPath(REQUEST_PATH_INITIAL_RESOURCE_VERSION)
                .andReturn(200, podList(INITIAL_RESOURCE_VERSION))
                .once();
    }

    private void initQueue() {
        queueService.sendObject(ProcessingCoreQueueNames.KUBERNETES_EVENTS, "test-message");
        Message message = queueService.receive(ProcessingCoreQueueNames.KUBERNETES_EVENTS);
        assertThat(message.getPayload()).isEqualTo("test-message");
    }
}