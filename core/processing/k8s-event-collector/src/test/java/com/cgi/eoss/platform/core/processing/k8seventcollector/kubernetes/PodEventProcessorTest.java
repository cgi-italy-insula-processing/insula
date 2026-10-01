package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;

import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils.ResourceBuilders.podResource;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PodEventProcessorTest {

    private final PodEventHandler podEventHandlerOne = mock(PodEventHandler.class);
    private final PodEventHandler podEventHandlerTwo = mock(PodEventHandler.class);

    private List<PodEventHandler> podEventHandlers;

    private PodEventProcessor eventProcessor;

    private InOrder inOrder;

    @Before
    public void setUp() {

        podEventHandlers = ImmutableList.of(podEventHandlerOne, podEventHandlerTwo);

        eventProcessor = new PodEventProcessor(podEventHandlers);

        inOrder = inOrder(podEventHandlerOne, podEventHandlerTwo);
    }

    @After
    public void shutdown() {
        Mockito.verifyNoMoreInteractions(podEventHandlerOne, podEventHandlerTwo);
    }

    @Test
    public void testProcess_DispatchesEventToSupportingPodEventHandler_WhenEventIsSupportedBySomePodEventHandler() {

        Response<V1Pod> mockWatchResponse = podResource("someEventType").build().toMockWatchResponse();
        when(podEventHandlerOne.supports(mockWatchResponse)).thenReturn(false);
        when(podEventHandlerTwo.supports(mockWatchResponse)).thenReturn(true);

        eventProcessor.process(mockWatchResponse);

        inOrder.verify(podEventHandlerOne).supports(mockWatchResponse);
        inOrder.verify(podEventHandlerTwo).supports(mockWatchResponse);
        inOrder.verify(podEventHandlerTwo).handlePodEvent(mockWatchResponse);
    }

    @Test
    public void testProcess_DoesNothing_WhenNoPodEventHandlerSupportsGivenEvent() {

        Response<V1Pod> mockWatchResponse = podResource("someEventType").build().toMockWatchResponse();
        when(podEventHandlerOne.supports(mockWatchResponse)).thenReturn(false);
        when(podEventHandlerTwo.supports(mockWatchResponse)).thenReturn(false);

        eventProcessor.process(mockWatchResponse);

        inOrder.verify(podEventHandlerOne).supports(mockWatchResponse);
        inOrder.verify(podEventHandlerTwo).supports(mockWatchResponse);
    }

    @Test
    public void testProcess_DoesNothing_WhenEventHasNoType() {

        eventProcessor.process(podResource(null).build().toMockWatchResponse());
    }

    @Test
    public void testProcess_DoesNothing_WhenEventHasNoPod() {

        Response<V1Pod> mockWatchResponse = podResource("someEventType").build().toMockWatchResponse();
        mockWatchResponse.object = null;

        eventProcessor.process(mockWatchResponse);
    }

    @Test
    public void testProcess_DoesNothing_WhenPodHasNoJobIdIdentifier() {

        eventProcessor.process(podResource(null).jobId(null).build().toMockWatchResponse());
    }

    @Test
    public void testProcess_DoesNothing_WhenPodHasNoIntJobIdIdentifier() {

        eventProcessor.process(podResource(null).intJobId(null).build().toMockWatchResponse());
    }

}
