package com.cgi.eoss.platform.core.processing.io.download;


import com.cgi.eoss.platform.core.processing.io.IoCoreConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { IoCoreConfig.class })
@TestPropertySource(properties = {
        "platform.core.io.downloader.simple-http.enabled=true",
        "platform.core.io.downloader.s3.enabled=true"
})
public class SimpleDownloaderFacadeIT {

    @Autowired
    private SimpleDownloaderFacade simpleDownloaderFacade;

    private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    private MockWebServer webServer;

    private Path root;

    @Before
    public void setUp() throws IOException {
        webServer = new MockWebServer();
        webServer.start();
        root = fs.getPath("/downloads");
    }

    @After
    public void tearDown() throws IOException {
        webServer.shutdown();
        fs.close();
    }

    @Test
    public void testDownload_SavesFileIntoTargetAndReturnsDownloadPath() throws Exception {
        webServer.enqueue(new MockResponse().setBody("file-content"));

        Path downloadedInput =  simpleDownloaderFacade.download(new DownloadRequest(
                webServer.url("/download/uri/file.txt").uri(),
                root.resolve("input"),
                null));

        assertThat(downloadedInput).isEqualTo(fs.getPath("/downloads/input/file.txt"));
        assertThat(readContent(downloadedInput)).isEqualTo("file-content");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("input"),
                root.resolve("input").resolve("file.txt"));

        assertThat(webServer.getRequestCount()).isEqualTo(1);
        assertGetHttpRequestMade("/download/uri/file.txt");
    }

    @Test
    public void testDownload_SavesFilesIntoTargetsAndReturnsMapOfDownloads_WhenRequestsAreMultiple() throws Exception {
        webServer.enqueue(new MockResponse().setBody("file-content-one"));
        webServer.enqueue(new MockResponse().setBody("file-content-two"));
        String requestOneUri = "/download/uriOne/fileOne.txt";
        String requestTwoUri = "/download/uriTwo/fileTwo.txt";

        Map<URI, Path> downloadedInputs = simpleDownloaderFacade.download(ImmutableList.of(
                new DownloadRequest(webServer.url(requestOneUri).uri(), root.resolve("inputOne"), null),
                new DownloadRequest(webServer.url(requestTwoUri).uri(), root.resolve("inputTwo"), null))
        );

        assertThat(downloadedInputs).isEqualTo(ImmutableMap.of(
                webServer.url(requestOneUri).uri(), fs.getPath("/downloads/inputOne/fileOne.txt"),
                webServer.url(requestTwoUri).uri(), fs.getPath("/downloads/inputTwo/fileTwo.txt"))
        );
        assertThat(readContent(downloadedInputs.get(webServer.url(requestOneUri).uri()))).isEqualTo("file-content-one");
        assertThat(readContent(downloadedInputs.get(webServer.url(requestTwoUri).uri()))).isEqualTo("file-content-two");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("inputOne"),
                root.resolve("inputOne").resolve("fileOne.txt"),
                root.resolve("inputTwo"),
                root.resolve("inputTwo").resolve("fileTwo.txt")
        );

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        assertGetHttpRequestMade("/download/uriOne/fileOne.txt");
        assertGetHttpRequestMade("/download/uriTwo/fileTwo.txt");
    }

    @Test
    public void testDownload_DownloadsAllRequestsInSameTargetDir_WhenRequestsHaveSameTarget() throws Exception {
        webServer.enqueue(new MockResponse().setBody("file-content-one"));
        webServer.enqueue(new MockResponse().setBody("file-content-two"));
        Path target = root.resolve("target");
        String fileOneUri = "fileOneUri/fileOne.txt";
        String fileTwoUri = "fileTwoUri/fileTwo.txt";

        Map<URI, Path> downloadedInputs = simpleDownloaderFacade.download(ImmutableList.of(
                new DownloadRequest(webServer.url(fileOneUri).uri(), target, null),
                new DownloadRequest(webServer.url(fileTwoUri).uri(), target, null))
        );
        assertThat(downloadedInputs).isEqualTo(ImmutableMap.of(
                webServer.url(fileOneUri).uri(), fs.getPath("/downloads/target/fileOne.txt"),
                webServer.url(fileTwoUri).uri(), fs.getPath("/downloads/target/fileTwo.txt"))
        );
        assertThat(readContent(downloadedInputs.get(webServer.url(fileOneUri).uri()))).isEqualTo("file-content-one");
        assertThat(readContent(downloadedInputs.get(webServer.url(fileTwoUri).uri()))).isEqualTo("file-content-two");
        assertThat(walk(root)).containsExactlyInAnyOrder(
                root,
                root.resolve("target"),
                root.resolve("target").resolve("fileOne.txt"),
                root.resolve("target").resolve("fileTwo.txt")
        );

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        assertGetHttpRequestMade("/fileOneUri/fileOne.txt");
        assertGetHttpRequestMade("/fileTwoUri/fileTwo.txt");
    }

    private String readContent(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void assertGetHttpRequestMade(String path) throws InterruptedException {
        RecordedRequest request = webServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getBodySize()).isEqualTo(0);
        assertThat(request.getPath()).isEqualTo(path);
    }

    private static List<Path> walk(Path p) throws IOException {
        try (Stream<Path> ps = Files.walk(p)) {
            return ps.collect(Collectors.toList());
        }
    }

}
