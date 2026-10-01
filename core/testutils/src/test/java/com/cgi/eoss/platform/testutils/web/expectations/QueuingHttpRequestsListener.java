package com.cgi.eoss.platform.testutils.web.expectations;

import com.cgi.eoss.platform.testutils.events.PlatformEventsQueue;

import okhttp3.mockwebserver.RecordedRequest;

/**
 * HTTP Request listener implementation that inserts events for every received HTTP Request
 * within a queue of Platform Events
 *
 * @author cantaveneraf
 *
 */
public class QueuingHttpRequestsListener implements HttpRequestsListener {

    /**
     * The type of event stored when an HTTP Request has been received
     */
    public static final String HTTP_REQUEST_RECEIVED = "HTTP_REQUEST_RECEIVED";

    private final PlatformEventsQueue eventsQueue;

    /**
     * Initialize an instance of this class with the provided PlatformEventsQueue
     *
     * @param eventsQueue
     *            The events queue where "HTTP request received" events will be stored
     */
    public QueuingHttpRequestsListener(PlatformEventsQueue eventsQueue) {
        this.eventsQueue = eventsQueue;
    }

    @Override
    public void onHttpRequestReceived(RecordedRequest request) {
        eventsQueue.pushEvent(HTTP_REQUEST_RECEIVED, request);

    }
}
