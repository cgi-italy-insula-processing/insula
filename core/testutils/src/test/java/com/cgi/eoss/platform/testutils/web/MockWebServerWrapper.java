package com.cgi.eoss.platform.testutils.web;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Slf4j
public class MockWebServerWrapper {

    @Data
    @Builder
    public static class Request {

        private final HttpMethod httpMethod;
        private final String path;
        private final Pattern pathPattern;
        private final byte[] body;
        private final boolean skipBodyTest;
        private final Consumer<RecordedRequest> customConsumer;

        private final Set<String> headersNames;

    }

    @Data
    @Builder
    public static class Response {

        private final HttpStatus status;
        private final byte[] body;
        private final SocketPolicy socketPolicy;
        private final int delayBody;

        private final Map<String, String> headers;
    }

    @Data
    @Builder
    public static class HttpCall {

        private final Request request;
        private final Response response;
    }

    private final List<Request> expectedRequests = new ArrayList<>();

    private final MockWebServer webServer;

    public MockWebServerWrapper(MockWebServer webServer) {
        this.webServer = webServer;
    }

    public void start() {
        start(null);
    }

    public void start(Integer port) {
        try {
            if (port == null) {
                webServer.start();
            } else {
                webServer.start(port);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void shutdown() {
        try {
            webServer.shutdown();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public MockWebServer unwrap() {
        return webServer;
    }

    public static Request request(HttpMethod httpMethod, String path) {
        return request(httpMethod, path, "");
    }

    public static Request request(HttpMethod httpMethod, Pattern path) {
        return request(httpMethod, path, "");
    }

    /**
     * The function prepares the request for the verifyExpectedRequests. The parameters HttpMethod, path will be checked
     * and if customTestFunction is != null will be called for a customer assertion
     *
     * @param httpMethod to check
     * @param path to check
     * @param customTestConsumer custom function
     * @return request to check in verifyExpectedRequests
     */
    public static Request request(HttpMethod httpMethod, String path, Consumer<RecordedRequest> customTestConsumer) {
        return Request.builder().httpMethod(httpMethod).path(path).body("".getBytes()).customConsumer(customTestConsumer).build();
    }

    public static Request request(HttpMethod httpMethod, String path, String body, Consumer<RecordedRequest> customTestConsumer) {
        return Request.builder().httpMethod(httpMethod).path(path).body(body.getBytes()).customConsumer(customTestConsumer).build();
    }

    public static Request request(HttpMethod httpMethod, String path, String body) {
        return Request.builder().httpMethod(httpMethod).path(path).body(body.getBytes()).build();
    }

    public static Request request(HttpMethod httpMethod, String path, String body, Set<String> headersNames) {
        return Request.builder().httpMethod(httpMethod).path(path).body(body.getBytes()).headersNames(headersNames).build();
    }

    public static Request request(HttpMethod httpMethod, Pattern path, String body) {
        return Request.builder().httpMethod(httpMethod).pathPattern(path).body(body.getBytes()).build();
    }

    public static Request request(HttpMethod httpMethod, String path, byte[] body) {
        return Request.builder().httpMethod(httpMethod).path(path).body(body).build();
    }

    public static Request requestCustomBodyTest(HttpMethod httpMethod, String path, Consumer<RecordedRequest> customTestConsumer) {
        return Request.builder().httpMethod(httpMethod).path(path).skipBodyTest(true).customConsumer(customTestConsumer).build();
    }

    public static Request requestCustomBodyTest(HttpMethod httpMethod, Pattern path, Consumer<RecordedRequest> customTestConsumer) {
        return Request.builder().httpMethod(httpMethod).pathPattern(path).skipBodyTest(true).customConsumer(customTestConsumer).build();
    }

    public static Request requestWithOutBodyTest(HttpMethod httpMethod, String path) {
        return Request.builder().httpMethod(httpMethod).path(path).skipBodyTest(true).body(null).build();
    }

    public static Response response(HttpStatus httpStatus) {
        return response(httpStatus, "", null);
    }

    public static Response response(HttpStatus httpStatus, SocketPolicy socketPolicy, String body) {
        return Response.builder().status(httpStatus).body(body.getBytes()).socketPolicy(socketPolicy).build();
    }

    public static Response response(HttpStatus httpStatus, SocketPolicy socketPolicy) {
        return response(httpStatus, socketPolicy, "");
    }

    public static Response response(HttpStatus httpStatus, Map<String, String> headers) {
        return response(httpStatus, "", headers);
    }

    public static Response response(HttpStatus status, String body) {
        return response(status, body.getBytes(), 0);
    }

    public static Response response(HttpStatus status, byte[] body) {
        return Response.builder().status(status).body(body).headers(null).build();
    }

    public static Response response(HttpStatus status, String body, Map<String, String> headers) {
        return Response.builder().status(status).body(body.getBytes()).headers(headers).build();
    }

    public static Response response(HttpStatus status, String body, int delay) {
        return response(status, body.getBytes(), delay);
    }

    public static Response response(HttpStatus status, byte[] body, int delay) {
        return Response.builder().status(status).body(body).headers(null).delayBody(delay).build();
    }

    public void expect(Request expectedRequest, Response expectedResponse) {
        prepareResponse(r -> {
            try (Buffer b = new Buffer()) {
                if (expectedResponse.status != null) {
                    r.setResponseCode(expectedResponse.status.value());
                }
                r.setBody(b.write(expectedResponse.body));
                r.setBodyDelay(expectedResponse.getDelayBody(), TimeUnit.MILLISECONDS);
            }
            Map<String, String> headers = expectedResponse.getHeaders();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    r.addHeader(entry.getKey() + ":" + entry.getValue());
                }
            }
            if (expectedResponse.getSocketPolicy() != null) {
                r.setSocketPolicy(expectedResponse.getSocketPolicy());
            }
        });
        expectedRequests.add(expectedRequest);
    }

    public void verifyExpectedRequests() {
        expectRequestCount(expectedRequests.size());
        for (Request expectedRequest : expectedRequests) {
            expectRequest(recordedRequest -> {

                LOG.info("Expected method: {}", expectedRequest.getHttpMethod());
                LOG.info("Actual method: {}", recordedRequest.getMethod());

                assertThat(recordedRequest.getMethod(), is(expectedRequest.httpMethod.toString()));

                assertPathMatches(expectedRequest, recordedRequest);

                if (!expectedRequest.skipBodyTest) {
                    String body = recordedRequest.getBody().readUtf8();
                    LOG.info("Expected body: {}", new String(expectedRequest.body));
                    LOG.info("Actual body: {}", body);
                    assertThat(body.getBytes(), is(expectedRequest.body));
                }

                if (expectedRequest.getHeadersNames() != null) {
                    assertHeadersNamesSetMatches(recordedRequest.getHeaders().toMultimap().keySet(), expectedRequest.getHeadersNames());
                }

                Consumer<RecordedRequest> consumer = expectedRequest.getCustomConsumer();
                if (consumer != null) {
                    consumer.accept(recordedRequest);
                }

            });
        }
    }

    public void verifyNoRequestsReceived() {
        if (webServer.getRequestCount() != 0) {
            throw new AssertionError("Expected no requests, but received some.");
        }
    }

    private void assertPathMatches(Request expectedRequest, RecordedRequest recordedRequest) {
        if (expectedRequest.pathPattern != null) {
            Pattern expected = expectedRequest.getPathPattern();
            String actual = recordedRequest.getPath();

            LOG.info("Expected pathPattern: '{}'", expected);
            LOG.info("Actual path: '{}'", actual);
            assertTrue(expected.matcher(actual).matches());
        } else {
            LOG.info("Expected path: {}", expectedRequest.getPath());
            LOG.info("Actual path: {}", recordedRequest.getPath());
            assertThat(recordedRequest.getPath(), is(expectedRequest.getPath()));
        }
    }
    private void assertHeadersNamesSetMatches(Set<String> recordedHeadersNames, Set<String> expectedHeadersNames) {
        LOG.info("Expected headers names set: {}", expectedHeadersNames);
        LOG.info("Actual headers names set: {}", recordedHeadersNames);
        assertThat(recordedHeadersNames, is(expectedHeadersNames));
    }

    private void prepareResponse(Consumer<MockResponse> consumer) {
        MockResponse response = new MockResponse();
        consumer.accept(response);
        LOG.info("Expected Response {}", response);
        webServer.enqueue(response);
    }

    private void expectRequest(Consumer<RecordedRequest> consumer) {
        try {
            consumer.accept(webServer.takeRequest());
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void expectRequestCount(int count) {
        assertThat(webServer.getRequestCount(), is(count));
    }

}
