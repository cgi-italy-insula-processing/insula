package com.cgi.eoss.platform.core.processing.io.download;


import com.cgi.eoss.platform.core.processing.io.ServiceIoException;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class SimpleHttpDownloaderTest {

    private MockWebServer server;
    private FileSystem fileSystem;
    private Path root;
    private SimpleHttpDownloader downloader;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        root = fileSystem.getPath("/root");
        Files.createDirectories(root);

        downloader = new SimpleHttpDownloader(new OkHttpClient());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        fileSystem.close();
    }


    @Test
    public void testGetProtocols_ReturnsHttpAndHttps() {
        assertThat(downloader.getProtocols()).containsExactlyInAnyOrder("http", "https");
    }

    @Test
    public void testDownload_DownloadsAndSavesFile_WhenResponseIsSuccessful() throws Exception {
        server.enqueue(new MockResponse().setBody("file-content"));

        Path result = downloader.download(
                new DownloadRequest(server.url("/download/uri/file.txt").uri(), root, null));

        assertThat(result).isEqualTo(root.resolve("file.txt"));
        assertThat(readContent(result)).isEqualTo("file-content");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("file.txt"));

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertGetHttpRequestMade("/download/uri/file.txt");
    }

    @Test
    public void testDownload_DownloadsAndSavesFileWithSpecifiedName_WhenContentDispositionHeaderIsPresentAndValid() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("file-content")
                .addHeader("Content-Disposition", "attachment; filename=\"custom-name.txt\""));

        Path result = downloader.download(
                new DownloadRequest(server.url("/download/uri/file.txt").uri(), root, null));

        assertThat(result).isEqualTo(root.resolve("custom-name.txt"));
        assertThat(readContent(result)).isEqualTo("file-content");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("custom-name.txt"));

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertGetHttpRequestMade("/download/uri/file.txt");
    }

    @Test
    public void testDownload_DownloadsAndSavesFileWithNameFromUri_WhenContentDispositionHeaderDoesNotMatchPattern() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("file-content")
                .addHeader("Content-Disposition", "attachment; filename=unquoted.txt"));

        Path result = downloader.download(
                new DownloadRequest(server.url("/download/uri/original.txt").uri(), root, null));

        assertThat(result).isEqualTo(root.resolve("original.txt"));
        assertThat(readContent(result)).isEqualTo("file-content");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("original.txt"));

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertGetHttpRequestMade("/download/uri/original.txt");
    }

    @Test
    public void testDownload_DownloadsAndSavesFileWithNameFromFirstMatchingHeader_WhenMultipleContentDispositionHeadersArePresent() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("file-content")
                .addHeader("Content-Disposition", "attachment; filename=\"name-one.txt\"")
                .addHeader("Content-Disposition", "attachment; filename=\"name-two.txt\""));

        Path result = downloader.download(
                new DownloadRequest(server.url("/download/uri/original.txt").uri(), root, null));

        assertThat(result).isEqualTo(root.resolve("name-one.txt"));
        assertThat(readContent(result)).isEqualTo("file-content");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("name-one.txt"));

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertGetHttpRequestMade("/download/uri/original.txt");
    }

    @Test
    public void testDownload_ThrowsServiceIoExceptionAndDoesNotCreateFile_WhenResponseIsNotSuccessful() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> downloader.download(
                new DownloadRequest(server.url("/missing.txt").uri(), root, null)))
                        .isInstanceOf(ServiceIoException.class)
                        .hasMessage("HTTP request failed with code 404");

        try (Stream<Path> files = Files.list(root)) {
            assertThat(files).isEmpty();
        }

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertGetHttpRequestMade("/missing.txt");
    }

    private void assertGetHttpRequestMade(String path) throws InterruptedException {
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getBodySize()).isEqualTo(0);
        assertThat(request.getPath()).isEqualTo(path);
    }

    private String readContent(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static List<Path> walk(Path p) throws IOException {
        try (Stream<Path> ps = Files.walk(p)) {
            return ps.collect(Collectors.toList());
        }
    }
}