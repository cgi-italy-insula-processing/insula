package com.cgi.eoss.platform.core.processing.io.download;


import com.cgi.eoss.platform.core.processing.io.ServiceIoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Simple {@link Downloader} implementation for resources served over HTTP/HTTPS.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "platform.core.io.downloader.simple-http.enabled", havingValue = "true")
public class SimpleHttpDownloader implements Downloader {

    private static final String FILENAME_HEADER = "Content-Disposition";
    private static final Pattern FILENAME_PATTERN = Pattern.compile(".*filename=\"(.*)\".*");
    private static final Set<String> PROTOCOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("http", "https")));

    private final OkHttpClient okHttpClient;

    @Override
    public Set<String> getProtocols() { return PROTOCOLS; }

    @Override
    public Path download(DownloadRequest downloadRequest) throws IOException {
        URI uri = downloadRequest.getDownloadUri();
        Path downloadFolder = downloadRequest.getDownloadFolder();
        LOG.info("Downloading: '{}' to destination '{}'", uri, downloadFolder);

        try (Response response = okHttpClient.newCall(createRequest(uri)).execute()){
            if (!response.isSuccessful()) {
                throw new ServiceIoException("HTTP request failed with code " + response.code());
            }

            return saveFile(downloadFolder, uri, response);
        }
    }

    private Path saveFile(Path targetDir, URI uri, Response response) throws IOException {
        Path outputPath = targetDir.resolve(getFilename(uri, response.headers(FILENAME_HEADER)));

        try (BufferedSink sink = Okio.buffer(Okio.sink(outputPath))) {
            sink.writeAll(response.body().source());
        }

        LOG.info("Successfully downloaded '{}' to '{}' via HTTP(s)", uri, outputPath);
        return outputPath;
    }

    private static Request createRequest(URI uri) throws MalformedURLException {
        return new Request.Builder().url(uri.toURL()).build();
    }

    private static String getFilename(URI uri, List<String> headers) {
        return headers.stream()
                .map(FILENAME_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElse(Paths.get(uri.getPath()).getFileName().toString());
    }
}
