package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesAsyncEventCollectorTest {

    private KubernetesResourceEventCollector eventCollector;
    private KubernetesAsyncEventCollector asyncEventCollector;
    private long eventCollectDelayMillis = 500;

    @Before
    public void init() {
        eventCollector = mock(KubernetesResourceEventCollector.class);
        asyncEventCollector = new KubernetesAsyncEventCollector("mock",
                Executors.newSingleThreadScheduledExecutor(),
                eventCollector,
                eventCollectDelayMillis);
    }

    @Test
    public void testCollectEventsMultipleTimesUntilStopped() throws Exception {
        AtomicLong firstCallTimestamp = new AtomicLong();
        AtomicLong secondCallTimestamp = new AtomicLong();
        doAnswer(i -> {
            firstCallTimestamp.set(System.nanoTime());
            LOG.info("Collector called for the first time");
            return null;
        }).doAnswer(i -> {
            secondCallTimestamp.set(System.nanoTime());
            LOG.info("Collector called for the second time..will stop");
            asyncEventCollector.stop();
            return null;
        }).when(eventCollector).collect();

        asyncEventCollector.start();

        assertFalse(asyncEventCollector.isStopped());

        await().atMost(1, TimeUnit.SECONDS).until(() -> asyncEventCollector.isStopped());

        verify(eventCollector, times(2)).collect();

        long actualEventCollectDelayMillis = Duration.of(secondCallTimestamp.get() - firstCallTimestamp.get(), ChronoUnit.NANOS).toMillis();
        assertThat(actualEventCollectDelayMillis, is(greaterThanOrEqualTo(eventCollectDelayMillis)));
        assertThat(actualEventCollectDelayMillis, is(lessThanOrEqualTo(eventCollectDelayMillis + 100)));
    }

    @Test
    public void testExceptionsThrownWhileCollectingEventsDoNotStopTheLoop() throws Exception {

        doAnswer(i -> {
            LOG.info("Collector called for the first time");
            return null;
        }).doThrow(new IllegalStateException("Collection failure"))
                .doAnswer(i -> {
                    LOG.info("Collector called for the third time..will stop");
                    asyncEventCollector.stop();
                    return null;
                }).when(eventCollector).collect();

        asyncEventCollector.start();

        await().atMost(2, TimeUnit.SECONDS).until(() -> asyncEventCollector.isStopped());

        verify(eventCollector, times(3)).collect();
    }

    @Test
    public void testStartingAnAlreadyStartedCollectorThrowsException() throws Exception {

        doAnswer(i -> {
            LOG.info("Collector called..");
            return null;
        }).when(eventCollector).collect();

        asyncEventCollector.start();

        try {
            asyncEventCollector.start();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("Can't start event collector for resource mock: already started"));
        }

        assertFalse(asyncEventCollector.isStopped());

        asyncEventCollector.stop();

        assertTrue(asyncEventCollector.isStopped());

    }

    @Test
    public void testStoppingNotStartedCollectorThrowsException() throws Exception {

        try {
            asyncEventCollector.stop();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("Can't stop event collector for resource mock: was not started"));
        }

    }
}
