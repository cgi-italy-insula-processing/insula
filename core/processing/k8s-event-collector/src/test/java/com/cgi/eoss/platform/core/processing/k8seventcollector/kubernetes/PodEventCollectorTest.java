package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;


import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.PodResource;
import com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.WatchResponse;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podList;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podResource;
import static com.cgi.eoss.platform.testutils.json.JsonUtils.serialize;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

public class PodEventCollectorTest {

    private static final String REQUEST_PATH_INITIAL_RESOURCE_VERSION = "/k8/api/v1/namespaces/ns-platform/pods";
    private DefaultMockServer webServer;

    private PodEventProcessor podEventProcessor;

    private PodEventCollector eventCollector;

    private InOrder inOrder;

    @Before
    public void setUp() {

        podEventProcessor = mock(PodEventProcessor.class);
        inOrder = inOrder(podEventProcessor);

        webServer = new DefaultMockServer();
        webServer.start();

        String k8Url = webServer.url("/k8").replace("localhost", "127.0.0.1");

        ApiClient apiClient = Config.fromUrl(k8Url);
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder()
                .readTimeout(3, TimeUnit.SECONDS)
                .build());
        eventCollector = new PodEventCollector(
                new CoreV1Api(apiClient),
                podEventProcessor,
                "ns-platform",
                30);
    }

    @After
    public void shutdown() {
        webServer.shutdown();
        Mockito.verifyNoMoreInteractions(podEventProcessor);
    }

    @Test
    public void testCollect_UsesRetrievedResourceVersionAndReturnsNewOneAfterSuccessfullyDelegatingResponseProcessing_WhenResourceVersionIsInitiallyEmpty() throws Exception {

        String initialResourceVersion = "100";

        mockInitialResourceVersionRetrieval(initialResourceVersion);

        PodResource podResource = podResource("MODIFIED", "101")
                .phase("Running").build();

        WatchResponse<V1Pod> watchResponse = podResource.toWatchResponse();
        //uses initially retrieved resource version to execute watch call
        String watchRequestPath = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion=" + initialResourceVersion + "&timeoutSeconds=30&watch=true";
        webServer.expect()
                .get()
                .withPath(watchRequestPath)
                .andReturn(200, watchResponse)
                .once();
        //returns new resource version
        assertThat(eventCollector.collect()).isEqualTo("101");

        ArgumentCaptor<Watch.Response<V1Pod>> responseArgumentCaptor = ArgumentCaptor.forClass(Watch.Response.class);

        inOrder.verify(podEventProcessor).process(responseArgumentCaptor.capture());

        Watch.Response<V1Pod> actualEvent = responseArgumentCaptor.getValue();
        assertThat(actualEvent.type).isEqualTo(watchResponse.getType());
        assertThat(actualEvent.object).isEqualTo(watchResponse.getObject());

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
            assertThat(request.getPath()).isEqualTo(watchRequestPath);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    @Test
    public void testCollect_UsesLatestRetrievedResourceVersionAndDelegatesRetrievedResponseProcessing_WhenResourceVersionIsInitiallyValued() throws Exception {
        // mock precondition to make the resource version not empty
        String latestResourceVersion = "101";
        {
            String initialResourceVersion = "100";
            mockInitialResourceVersionRetrieval(initialResourceVersion);

            PodResource podResource = podResource("MODIFIED", latestResourceVersion)
                    .phase("Running").build();
            WatchResponse<V1Pod> watchResponse = podResource.toWatchResponse();
            //uses initially retrieved resource version to execute watch call
            String watchRequestPath = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion=" + initialResourceVersion + "&timeoutSeconds=30&watch=true";
            webServer.expect()
                    .get()
                    .withPath(watchRequestPath)
                    .andReturn(200, watchResponse)
                    .once();
            //returns new resource version
            assertThat(eventCollector.collect()).isEqualTo(latestResourceVersion);
            inOrder.verify(podEventProcessor).process(any());

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
                assertThat(request.getPath()).isEqualTo(watchRequestPath);
                assertThat(request.getBody().readUtf8()).isEmpty();
            }
        }
        // end preconditions

        String newResourceVersion = "102";
        PodResource podResource = podResource("MODIFIED", newResourceVersion)
                .phase("Running").build();

        WatchResponse<V1Pod> watchResponse = podResource.toWatchResponse();
        //uses latest resource version retrieved to execute the new watch call:
        String watchRequestPath = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion=" + latestResourceVersion + "&timeoutSeconds=30&watch=true";
        webServer.expect()
                .get()
                .withPath(watchRequestPath)
                .andReturn(200, watchResponse)
                .once();
        //returns the new resource version retrieved from last call
        assertThat(eventCollector.collect()).isEqualTo(newResourceVersion);

        ArgumentCaptor<Watch.Response<V1Pod>> responseArgumentCaptor = ArgumentCaptor.forClass(Watch.Response.class);

        inOrder.verify(podEventProcessor).process(responseArgumentCaptor.capture());

        Watch.Response<V1Pod> actualEvent = responseArgumentCaptor.getValue();
        assertThat(actualEvent.type).isEqualTo(watchResponse.getType());
        assertThat(actualEvent.object).isEqualTo(watchResponse.getObject());
        assertThat(webServer.getRequestCount()).isEqualTo(3);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(watchRequestPath);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    @Test
    public void testCollect_DispatchesMultipleMessages_WhenWatchReturnsMultipleEvents() throws Exception {

        String initialResourceVersion = "100";
        mockInitialResourceVersionRetrieval(initialResourceVersion);

        WatchResponse<V1Pod> watchResponseOne = podResource("MODIFIED", "101")
                .phase("Succeeded")
                .build().toWatchResponse();
        WatchResponse<V1Pod> watchResponseTwo = podResource("MODIFIED", "102")
                .phase("Running")
                .build().toWatchResponse();

        String watchRequestPath = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion="+initialResourceVersion+"&timeoutSeconds=30&watch=true";
        webServer.expect()
                .get()
                .withPath(watchRequestPath)
                .andReturn(200,
                        serialize(watchResponseOne) + System.lineSeparator() + serialize(watchResponseTwo))
                .once();

        assertThat(eventCollector.collect()).isEqualTo("102");

        ArgumentCaptor<Watch.Response<V1Pod>> responseArgumentCaptor = ArgumentCaptor.forClass(Watch.Response.class);

        inOrder.verify(podEventProcessor, times(2)).process(responseArgumentCaptor.capture());

        List<Watch.Response<V1Pod>> capturedEvents = responseArgumentCaptor.getAllValues();
        assertThat(capturedEvents).hasSize(2);
        Watch.Response<V1Pod> actualEventOne = capturedEvents.get(0);
        assertThat(actualEventOne.type).isEqualTo(watchResponseOne.getType());
        assertThat(actualEventOne.object).isEqualTo(watchResponseOne.getObject());
        Watch.Response<V1Pod> actualEventTwo = capturedEvents.get(1);
        assertThat(actualEventTwo.type).isEqualTo(watchResponseTwo.getType());
        assertThat(actualEventTwo.object).isEqualTo(watchResponseTwo.getObject());

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
            assertThat(request.getPath()).isEqualTo(watchRequestPath);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }


    }

    @Test
    public void testCollect_ThrowsApiException_WhenInitialResourceVersionRetrievalIsUnsuccessful() throws Exception {
        //uses latest resource version retrieved to execute the new watch call:
        webServer.expect()
                .get()
                .withPath(REQUEST_PATH_INITIAL_RESOURCE_VERSION)
                .andReturn(500, null)
                .once();

        //returns the new resource version retrieved from last call
        assertThatThrownBy(() -> eventCollector.collect())
                .isInstanceOf(ApiException.class)
                .hasMessage("Server Error");
        assertThat(webServer.getRequestCount()).isEqualTo(1);
        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(REQUEST_PATH_INITIAL_RESOURCE_VERSION);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    @Test
    public void testCollect_ThrowsApiException_WhenWatchRequestIsUnsuccessful() throws Exception {
        String initialResourceVersion = "100";
        mockInitialResourceVersionRetrieval(initialResourceVersion);
        //uses latest resource version retrieved to execute the new watch call:
        String watchRequestPath = "/k8/api/v1/namespaces/ns-platform/pods?resourceVersion=" + initialResourceVersion + "&timeoutSeconds=30&watch=true";
        webServer.expect()
                .get()
                .withPath(watchRequestPath)
                .andReturn(500, null)
                .once();

        //returns the new resource version retrieved from last call
        assertThatThrownBy(() -> eventCollector.collect())
                .isInstanceOf(ApiException.class)
                .hasMessage("Server Error");
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
            assertThat(request.getPath()).isEqualTo(watchRequestPath);
            assertThat(request.getBody().readUtf8()).isEmpty();
        }
    }

    private void mockInitialResourceVersionRetrieval(String initialResourceVersion) {
        webServer.expect()
                .get()
                .withPath(REQUEST_PATH_INITIAL_RESOURCE_VERSION)
                .andReturn(200, podList(initialResourceVersion))
                .once();
    }
}
