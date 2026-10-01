package com.cgi.eoss.platform.testutils.web.expectations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.Builder;
import lombok.Value;
import okhttp3.Headers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Data class that holds details of an HTTP Response that can be used to
 * form an HTTP Call expectation
 *
 * @author cantaveneraf
 *
 */
@Value
@Builder
public class ExpectedHttpResponse {

    private final String body;
    private final Integer responseCode;
    private final Long bodyDelayMillis;
    private final Map<String, String> headers;
    private final SocketPolicy socketPolicy;

    /**
     * Create a builder for an Expected HTTP Response that simulates
     * a no-response from the socket opened on the server side.
     *
     * @return
     *         The Expected HTTP Response builder
     */
    public static ExpectedHttpResponseBuilder noResponse() {
        return ExpectedHttpResponse.builder()
                .socketPolicy(SocketPolicy.NO_RESPONSE);
    }

    /**
     * Create a builder for an Expected HTTP Response with status code 200 (OK)
     * and without body.
     *
     * @return
     *         The Expected HTTP Response builder
     */
    public static ExpectedHttpResponseBuilder ok() {
        final String noBody = null;
        return ok(noBody);
    }

    /**
     * Create a builder for an Expected HTTP Response with status code 200 (OK)
     * and with the provided body.
     *
     * @param body
     *            The file containing the body of the response
     *
     * @return
     *         The Expected HTTP Response builder
     */
    public static ExpectedHttpResponseBuilder ok(Path body) {
        try {
            return ok(new String(Files.readAllBytes(body)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create a builder for an Expected HTTP Response with status code 200 (OK)
     * and with the provided body.
     *
     * @param body
     *            The body of the response
     *
     * @return
     *         The Expected HTTP Response builder
     */
    public static ExpectedHttpResponseBuilder ok(String body) {
        return status(200)
                .body(body);
    }

    /**
     * Create a builder for an Expected HTTP Response with the provided status code
     * and without body.
     *
     * @param responseCode
     *            The response status code
     *
     * @return
     *         The Expected HTTP Response builder
     */
    public static ExpectedHttpResponseBuilder status(int responseCode) {
        return ExpectedHttpResponse.builder()
                .responseCode(responseCode);
    }

    /**
     * Create a MockResponse object initialized with attributes from this object
     *
     * @return
     *         A MockResponse with attributes initialized from this object
     */
    public MockResponse toMockResponse() {
        MockResponse response = new MockResponse();
        if (body != null) {
            response.setBody(body);
        }
        if (socketPolicy != null) {
            response.setSocketPolicy(socketPolicy);
        }

        if (headers != null) {
            response.setHeaders(Headers.of(headers));
        }
        if (responseCode != null) {
            response.setResponseCode(responseCode);
        }
        if (bodyDelayMillis != null) {
            response.setBodyDelay(bodyDelayMillis, TimeUnit.MILLISECONDS);
        }
        return response;
    }

}
