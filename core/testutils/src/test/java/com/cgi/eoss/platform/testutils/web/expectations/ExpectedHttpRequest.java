package com.cgi.eoss.platform.testutils.web.expectations;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cgi.eoss.platform.testutils.core.StringUtils;

import lombok.Builder;
import lombok.Value;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Data class that holds details of an HTTP Request that can be used to
 * form an HTTP Call expectation
 *
 * @author cantaveneraf
 *
 */
@Value
@Builder
public class ExpectedHttpRequest {
    private final String method;
    private final String path;
    private final String body;
    private final Map<String, List<String>> headers;

    /**
     * Create a builder for an Expected HTTP Request for the GET method and to the provided path
     *
     * @param path
     *            The path of the request
     * @return
     *         The Expected HTTP Request builder
     */
    public static ExpectedHttpRequestBuilder get(String path) {
        return get(path, new HashMap<>());
    }

    /**
     * Create a builder for an Expected HTTP Request for the GET method, to the provided path and with the provided headers.
     *
     *
     * @param path
     *            The path of the request
     * @param headers
     *            The headers to attach to the HTTP Request
     * @return
     *         The Expected HTTP Request builder
     */
    public static ExpectedHttpRequestBuilder get(String path, Map<String, List<String>> headers) {
        return request("GET", path, "", new HashMap<>());
    }

    /**
     * Create a builder for an Expected HTTP Request for the POST method and to the provided path
     *
     * @param path
     *            The path of the request
     * @return
     *         The Expected HTTP Request builder
     */
    public static ExpectedHttpRequestBuilder post(String path) {
        return post(path, "");
    }

    /**
     * Create a builder for an Expected HTTP Request for the POST method, the provided path
     * and with the provided body
     *
     * @param path
     *            The path of the request
     * @param body
     *            The file containing the body of the request
     * @return
     *         The Expected HTTP Request builder
     */
    public static ExpectedHttpRequestBuilder post(String path, Path body) {
        return post(path, StringUtils.readAsString(body), new HashMap<>());
    }

    /**
     * Create a builder for an Expected HTTP Request for the POST method, the provided path
     * and with the provided body
     *
     * @param path
     *            The path of the request
     * @param body
     *            The body of the request
     * @return
     *         The Expected HTTP Request builder
     */
    public static ExpectedHttpRequestBuilder post(String path, String body) {
        return post(path, body, new HashMap<>());
    }

    /**
     * Create a builder for an Expected HTTP Request for the POST method, the provided path
     * and with the provided body
     *
     * @param path
     *            The path of the request
     * @param body
     *            The body of the request
     * @param headers
     *            The headers to attach to the HTTP Request
     * @return
     *         The Expected HTTP Request builder
     */
    public static ExpectedHttpRequestBuilder post(String path, String body, Map<String, List<String>> headers) {
        return request("POST", path, body, new HashMap<>());
    }

    /**
     * Create an instance of ExpectedHttpRequest from the provided RecordedRequest
     *
     * @param recordedRequest
     *            The source request
     * @return
     *         The ExpectedHttpRequest initialized from the RecordedRequest
     */
    public static ExpectedHttpRequest from(RecordedRequest recordedRequest) {
        return ExpectedHttpRequest.builder()
                .path(recordedRequest.getPath())
                .method(recordedRequest.getMethod())
                .body(recordedRequest.getBody().readUtf8())
                .headers(recordedRequest.getHeaders().toMultimap())
                .build();
    }

    private static ExpectedHttpRequestBuilder request(String method, String path, String body, Map<String, List<String>> headers) {
        return ExpectedHttpRequest.builder()
                .path(path)
                .method(method)
                .body(body)
                .headers(headers);
    }

}
