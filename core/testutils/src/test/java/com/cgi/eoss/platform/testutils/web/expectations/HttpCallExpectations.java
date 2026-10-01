package com.cgi.eoss.platform.testutils.web.expectations;

import static com.cgi.eoss.platform.testutils.web.expectations.HttpCallExpectation.expectation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cgi.eoss.platform.testutils.web.expectations.ExpectedHttpRequest.ExpectedHttpRequestBuilder;
import com.cgi.eoss.platform.testutils.web.expectations.ExpectedHttpResponse.ExpectedHttpResponseBuilder;
import com.cgi.eoss.platform.testutils.web.matchers.ObjectMatcher;

/**
 * Class that models a list of expected HTTP Calls.
 *
 * @author cantaveneraf
 *
 */
public class HttpCallExpectations {
    private final List<HttpCallExpectation> expectations = new ArrayList<>();

    /**
     * Add 'times' expectations made of the provided request and response
     *
     * @param request
     *            The expected HTTP Request
     * @param response
     *            The expected HTTP Response
     * @param times
     *            The number of times the expectations must be repeated
     * @return
     *         This object
     */
    public HttpCallExpectations add(ExpectedHttpRequestBuilder request, ExpectedHttpResponseBuilder response, int times) {
        for (int i = 0; i < times; i++) {
            add(request, response);
        }
        return this;
    }

    /**
     * Add an expectation made of the provided request, response and request matcher
     *
     * @param request
     *            The expected HTTP Request
     * @param response
     *            The expected HTTP Response
     * @param requestMatcher
     *            The matcher of HTTP Requests
     * @return
     *         This object
     */
    public HttpCallExpectations add(ExpectedHttpRequestBuilder request, ExpectedHttpResponseBuilder response, ObjectMatcher<ExpectedHttpRequest> requestMatcher) {
        expectations.add(expectation(request, response, requestMatcher));
        return this;
    }

    /**
     * Add an expectation made of the provided request and response
     *
     * @param request
     *            The expected HTTP Request
     * @param response
     *            The expected HTTP Response
     * @return
     *         This object
     */
    public HttpCallExpectations add(ExpectedHttpRequestBuilder request, ExpectedHttpResponseBuilder response) {
        expectations.add(expectation(request, response));
        return this;
    }

    /**
     * Return an unmodifiable view of the expectations wrapped by this class
     *
     * @return
     *         An unmodifiable view of the expectations wrapped by this class
     */
    public List<HttpCallExpectation> asList() {
        return Collections.unmodifiableList(expectations);
    }

}
