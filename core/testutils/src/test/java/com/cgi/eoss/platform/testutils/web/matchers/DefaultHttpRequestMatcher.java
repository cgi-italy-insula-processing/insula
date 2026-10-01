package com.cgi.eoss.platform.testutils.web.matchers;

import java.util.List;
import java.util.Map;

import com.cgi.eoss.platform.testutils.web.expectations.ExpectedHttpRequest;

import lombok.Getter;
import lombok.Setter;

/**
 * Class that exposes methods to compare the content of http requests.
 * The logic to compare individual parts of the Http Requests (method, path, body, headers etc.)
 * can be customized by setting the appropriate ObjectMatcher
 *
 * @author cantaveneraf
 *
 */
@Getter
@Setter
public class DefaultHttpRequestMatcher implements ObjectMatcher<ExpectedHttpRequest> {

    private ObjectMatcher<String> methodComparator = new EqualityMatcher<>();
    private ObjectMatcher<String> pathComparator = new EqualityMatcher<>();
    private ObjectMatcher<String> bodyComparator = new EqualityMatcher<>();
    private ObjectMatcher<Map<String, List<String>>> headersComparator = new MultimapSubsetMatcher<>();

    @Override
    public void match(ExpectedHttpRequest actualRequest, ExpectedHttpRequest expectedRequest) {
        methodComparator.match(actualRequest.getMethod(), expectedRequest.getMethod());
        pathComparator.match(actualRequest.getPath(), expectedRequest.getPath());
        bodyComparator.match(actualRequest.getBody(), expectedRequest.getBody());
        headersComparator.match(actualRequest.getHeaders(), expectedRequest.getHeaders());
    }

}
