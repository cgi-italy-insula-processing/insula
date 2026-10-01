package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Downloads a STAC Document over HTTP or HTTPS Protocols.
 */
@AllArgsConstructor
public class HttpStacDownloader implements StacDownloader {

    private final OkHttpClient stacClient;

    private final ObjectMapper objectMapper;

    private final StacInputsServiceProperties stacInputsServiceProperties;

    @Override
    public boolean supports(URL url) {
        return "http".equals(url.getProtocol()) || "https".equals(url.getProtocol());
    }

    @Override
    public StacDocument download(URL url, String username) {
        Request request = buildRequest(url);
        try (Response response = stacClient.newCall(request).execute()) {
            return parseResponse(response);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String readResponse(ResponseBody responseBody) throws IOException {
        StringBuilder responseBuilder = new StringBuilder();
        int maximumSize = stacInputsServiceProperties.getMaxDocumentSize() + 1;
        try (InputStream responseStream = responseBody.byteStream()) {
            readUntilMaximumSizeIsReached(responseBuilder, responseStream, maximumSize);
        }
        return responseBuilder.toString();
    }

    private static void readUntilMaximumSizeIsReached(StringBuilder responseBuilder,
                                                      InputStream in, int maximumSize) throws IOException {
        byte[] buffer = new byte[maximumSize];
        int cumulativeBytesRead = 0;
        int currentBytesRead = 0;
        while (isReadingStream(currentBytesRead) && cumulativeBytesRead < maximumSize) {
            currentBytesRead = in.read(buffer);
            if (isReadingStream(currentBytesRead)) {
                String currentRead = new String(buffer, 0, currentBytesRead);
                responseBuilder.append(currentRead);
                cumulativeBytesRead += currentBytesRead;
            }
        }
        if (isReadingStream(currentBytesRead)) {
            throw new IOException("Response exceeds maximum expected size " + maximumSize);
        }
    }

    private static boolean isReadingStream(int bytesRead) {
        return bytesRead != -1;
    }

    private static Request buildRequest(URL stacUrl) {
        return new Request.Builder().url(stacUrl).header("Accept", "application/geo+json").get().build();
    }

    private StacDocument parseResponse(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        if (!response.isSuccessful() || responseBody == null) {
            throw new IllegalStateException(
                    "Response returned with unexpected status code " + response.code() + " or null body.");
        }
        return objectMapper.readValue(readResponse(responseBody), StacDocument.class);
    }
}