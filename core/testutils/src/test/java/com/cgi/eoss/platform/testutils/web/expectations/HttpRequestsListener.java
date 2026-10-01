package com.cgi.eoss.platform.testutils.web.expectations;

import okhttp3.mockwebserver.RecordedRequest;

/**
 * Listener interface that defines the method to be invoked upon reception of HTTP Requests
 *
 * @author cantaveneraf
 *
 */
public interface HttpRequestsListener {

    /**
     * Inform the listener that an HTTP Request was received
     *
     * @param request
     *            The received HTTP request
     */
    void onHttpRequestReceived(RecordedRequest request);

}
