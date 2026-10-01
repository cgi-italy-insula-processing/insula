package com.cgi.eoss.platform.testutils.web.expectations;

import com.cgi.eoss.platform.testutils.web.expectations.ExpectedHttpRequest.ExpectedHttpRequestBuilder;
import com.cgi.eoss.platform.testutils.web.expectations.ExpectedHttpResponse.ExpectedHttpResponseBuilder;
import com.cgi.eoss.platform.testutils.web.matchers.DefaultHttpRequestMatcher;
import com.cgi.eoss.platform.testutils.web.matchers.ObjectMatcher;

import lombok.Value;

/**
 * Class that models an expected HTTP Call made of an HTTP Request, an HTTP Response
 * and a matcher to verify the HTTP Request expectation.
 *
 * @author cantaveneraf
 *
 */
@Value
public class HttpCallExpectation {
    private final ExpectedHttpRequest request;
    private final ExpectedHttpResponse response;
    private final ObjectMatcher<ExpectedHttpRequest> requestMatcher;

    /**
     * Initialize an HTTP Call Expectation with the provided request and response
     *
     * @param request
     *            The expected HTTP Request
     * @param response
     *            The expected HTTP Response
     * @return
     *         The HTTP Call expectation
     */
    public static HttpCallExpectation expectation(ExpectedHttpRequestBuilder request, ExpectedHttpResponseBuilder response) {
        return expectation(request, response, new DefaultHttpRequestMatcher());
    }

    /**
     * Initialize an HTTP Call Expectation with the provided request and response
     *
     * @param request
     *            The expected HTTP Request
     * @param response
     *            The expected HTTP Response
     * @param requestMatcher
     *            The matcher of HTTP Requests
     *
     * @return
     *         The HTTP Call expectation
     */
    public static HttpCallExpectation expectation(ExpectedHttpRequestBuilder request, ExpectedHttpResponseBuilder response, ObjectMatcher<ExpectedHttpRequest> requestMatcher) {
        return new HttpCallExpectation(request.build(), response.build(), requestMatcher);
    }

}
