package com.cgi.eoss.platform.testutils.events;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

public class PlatformEventsQueueTest {

    private PlatformEventsQueue eventsQueue = new PlatformEventsQueue(new LinkedBlockingQueue<PlatformEvent>());

    @Test
    public void testPlatformEventsQueueSizeGrowsWhenEventIsPushed() {

        assertThat(eventsQueue.size(), is(0));

        eventsQueue.pushEvent("EventType", "EventContent");

        assertThat(eventsQueue.size(), is(1));
    }

    @Test
    public void testPlatformEventsCanBeRetrievedInFifo() throws InterruptedException {

        eventsQueue.pushEvent("EventTypeA", "One");
        Thread.sleep(200); // Wait some time before inserting a new event in order to generate different timestamps
        eventsQueue.pushEvent("EventTypeA", "Two");

        assertThat(eventsQueue.size(), is(2));

        PlatformEvent eventOne = eventsQueue.pollEvent(Duration.ofSeconds(0));
        PlatformEvent eventTwo = eventsQueue.pollEvent(Duration.ofSeconds(0));

        assertThat(eventOne.getType(), is("EventTypeA"));
        assertThat(eventTwo.getType(), is("EventTypeA"));

        assertThat(eventOne.getValue(), is("One"));
        assertThat(eventTwo.getValue(), is("Two"));

        long timeStampDiff = eventTwo.getTimestamp() - eventOne.getTimestamp();
        assertThat(timeStampDiff, allOf(greaterThanOrEqualTo(200L), lessThanOrEqualTo(300L)));

    }

    @Test
    public void testPollEventByTypeReturnsTheEventWhenEventTypeMatches() {

        long beforePush = Instant.now().toEpochMilli();
        eventsQueue.pushEvent("EventTypeA", "One");

        assertThat(eventsQueue.size(), is(1));

        PlatformEvent event = eventsQueue.pollEvent(Duration.ofSeconds(0), "EventTypeA");

        assertThat(eventsQueue.size(), is(0));

        assertThat(event.getType(), is("EventTypeA"));
        assertThat(event.getValue(), is("One"));

        assertThat(event.getTimestamp(), allOf(greaterThanOrEqualTo(beforePush),
                lessThanOrEqualTo(Instant.now().toEpochMilli())));

    }

    @Test
    public void testPollEventThrowsWhenRequestingWrongEventType() {

        eventsQueue.pushEvent("EventTypeA", "One");

        assertThat(eventsQueue.size(), is(1));

        try {
            eventsQueue.pollEvent(Duration.ofSeconds(0), "EventTypeB");
            fail();
        } catch (AssertionError e) {
            assertThat(e.getMessage(), is("\nExpected: is \"EventTypeB\"\n     but: was \"EventTypeA\""));
        }

        assertThat(eventsQueue.size(), is(0));

    }

    @Test
    public void testPollEventReturnsNullAfterWaitingForAMessageMoreThanMaxPollDuration() {

        assertThat(eventsQueue.size(), is(0));

        long beforePoll = Instant.now().toEpochMilli();
        assertNull(eventsQueue.pollEvent(Duration.ofMillis(500)));
        long afterPoll = Instant.now().toEpochMilli();

        assertThat(afterPoll - beforePoll, allOf(greaterThanOrEqualTo(500L),
                lessThanOrEqualTo(600L)));

    }
}
