package com.cgi.eoss.platform.testutils.web.expectations;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Implementation of the Dispatcher used by the MockWebServer that orderly delivers HTTP responses
 * only if the corresponding HTTP request match the configured expectations.
 * The optional listener is notified any time an HTTP request is received by this dispatcher.
 *
 * @author cantaveneraf
 *
 */
@Slf4j
public class ExpectationMatchingDispatcher extends Dispatcher {

    private final BlockingQueue<HttpCallExpectation> expectations;
    private final HttpRequestsListener listener;
    private boolean failed;

    /**
     * Initialize an instance of this class with the provided list of expected HTTP Calls and an optional request listener
     *
     * @param expectations
     *            The list of expected HTTP Request and the corresponding Responses to dispatch
     * @param listener
     *            An optional listener, notified whenever an HTTP Request is received
     */
    public ExpectationMatchingDispatcher(HttpCallExpectations expectations, HttpRequestsListener listener) {
        this.expectations = new LinkedBlockingQueue<>(expectations.asList());
        this.listener = listener;
        this.failed = false;
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        LOG.info("HTTP Request received for url {}", request.getRequestUrl());
        notifyRequestReceived(request);

        HttpCallExpectation expectation = pollExpectationOrThrow(request);

        ExpectedHttpRequest expectedRequest = expectation.getRequest();
        ExpectedHttpRequest actualRequest = ExpectedHttpRequest.from(request);
        LOG.info("Expected HTTP Request {}", expectedRequest);
        LOG.info("Actual HTTP Request {}", actualRequest);

        try {
            expectation.getRequestMatcher().match(actualRequest, expectedRequest);
        } catch (Throwable e) {
            LOG.warn("Unexpected HTTP Request {}", request, e);
            failed = true;
            throw e;
        }

        return expectation.getResponse().toMockResponse();
    }

    @Override
    public MockResponse peek() {
        return Optional.ofNullable(expectations.peek())
                .map(HttpCallExpectation::getResponse)
                .map(ExpectedHttpResponse::toMockResponse)
                .orElse(super.peek());

    }

    /**
     * Check whether all and only the expected HTTP Requests arrived
     *
     * @return
     *         true if all and only the expected HTTP Request arrived,
     *         false otherwise
     */
    public boolean allRequestsDispatched() {
        return !failed && expectations.isEmpty();
    }

    private void notifyRequestReceived(RecordedRequest request) {
        if (listener != null) {
            listener.onHttpRequestReceived(request);
        }
    }

    private HttpCallExpectation pollExpectationOrThrow(RecordedRequest request) {
        return Optional.ofNullable(expectations.poll())
                .orElseThrow(() -> new IllegalStateException("Unexpected HTTP request " + request));
    }

}
