package com.cgi.eoss.platform.testutils.events;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around a blocking in-memory queue (FIFO) that holds platform events.
 * This class exposes convenience methods to push and pull events from the queue
 *
 * @author cantaveneraf
 *
 */
@Slf4j
public class PlatformEventsQueue {

    private final BlockingQueue<PlatformEvent> eventsQueue;

    /**
     * Initialize an instance of this class with the provided blocking queue
     *
     * @param eventsQueue
     *            The queue that will hold the events
     */
    public PlatformEventsQueue(BlockingQueue<PlatformEvent> eventsQueue) {
        LOG.info("Creating event queue with id {}", System.identityHashCode(eventsQueue));
        this.eventsQueue = eventsQueue;
    }

    /**
     * Append a platform event to the tail of the queue
     *
     * @param type
     *            The type of the event
     * @param value
     *            The actual payload of the event
     * @return
     *         The event just added to the queue
     */
    public PlatformEvent pushEvent(String type, Object value) {
        LOG.info("Pushing event {} on queue id {}", type, System.identityHashCode(eventsQueue));
        PlatformEvent event = PlatformEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .value(value)
                .type(type)
                .build();
        eventsQueue.add(event);
        return event;
    }

    /**
     * Poll an event of a given type from the head of the queue waiting for the specified amount of time if the queue is empty.
     * Throw a runtime exception if the event is not of the expected type or the queue is empty for the poll duration.
     *
     * @param maxPollDuration
     *            The maximum amount of time to wait for an event to arrive
     *
     * @param type
     *            The expected event type
     * @return
     *         The platform event of the expected type from the head of the queue
     */
    public PlatformEvent pollEvent(Duration maxPollDuration, String type) {
        LOG.info("Waiting for event {} up to {}", type, maxPollDuration);
        PlatformEvent event = pollEvent(maxPollDuration);
        assertNotNull(event);
        assertThat(event.getType(), is(type));
        return event;
    }

    /**
     * Poll an event from the head of the queue waiting for the specified amount of time if the queue is empty
     *
     * @param maxPollDuration
     *            The maximum amount of time to wait for an event to arrive
     * @return
     *         The platform event from the head of the queue or null in case the queue was empty
     *         for the duration of the polling
     */
    public PlatformEvent pollEvent(Duration maxPollDuration) {
        try {
            LOG.info("Waiting for {}ms on queue id {}", maxPollDuration.toMillis(), System.identityHashCode(eventsQueue));
            PlatformEvent result = eventsQueue.poll(maxPollDuration.toMillis(), TimeUnit.MILLISECONDS);
            LOG.info("Waiting completed on queue id {} got event {}", System.identityHashCode(eventsQueue), result);
            return result;
        } catch (InterruptedException e) {
            LOG.error("Interrupted!!", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get the number of events currently on the queue
     *
     * @return
     *         The number of events currently on the queue
     */
    public int size() {
        return eventsQueue.size();
    }

}
