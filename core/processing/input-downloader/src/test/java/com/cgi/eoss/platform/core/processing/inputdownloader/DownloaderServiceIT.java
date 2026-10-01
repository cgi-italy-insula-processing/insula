package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.core.StringUtils;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { InputDownloaderCoreTestConfig.class })
@TestPropertySource(locations = {"classpath:test-input-downloader-core.properties"})
public abstract class DownloaderServiceIT {

    protected static final String INPUT_ID = "inputId";
    protected static final String USER_UUID = "user_uuid";
    protected static final Path ZIP_RESOURCE_PATH = Paths.get("src", "test", "resources", "files.zip");

    protected final MockWebServerWrapper webServer = new MockWebServerWrapper(new MockWebServer());

    @Autowired
    protected DownloaderService downloaderService;

    @Autowired
    protected FileSystem fileSystem;

    protected Path baseDir;

    protected String mockServerUrl;

    @Before
    public void setUp() {
        webServer.start();
        mockServerUrl = getMockServerUrl();
        baseDir = fileSystem.getPath("/basePath");
    }

    @After
    public void shutdown() throws IOException {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }

        FilesUtils.deleteDirContentsIfExists(baseDir);
        Files.deleteIfExists(baseDir);
    }

    @TestPropertySource(properties = {
            "platform.inputdownloader.createSubdirectories=true"
    })
    public static class DownloaderServiceCreateSubdirectoriesTrueIT extends DownloaderServiceIT {

        @Test
        public void testDownloadInputs_CreatesSubfoldersAndStoresFilesAndZip_WhenCreateSubdirIsTrue() throws IOException {
            webServer.expect(request(HttpMethod.GET, "/download/files.zip"),
                    response(HttpStatus.OK, Files.readAllBytes(ZIP_RESOURCE_PATH)));
            webServer.expect(request(HttpMethod.GET, "/download/file.xml"),
                    response(HttpStatus.OK, "test-file-content"));

            Multimap<String, Path> downloadedInputs = downloaderService.downloadInputs(
                    baseDir,
                    ImmutableList.of(createParam(
                            INPUT_ID,
                            ImmutableList.of(mockServerUrl + "download/files.zip", mockServerUrl + "download/file.xml"))),
                    USER_UUID);


            assertThat(downloadedInputs.keySet()).hasSize(1);
            assertThat(downloadedInputs.get(INPUT_ID)).containsExactlyInAnyOrder(
                    baseDir.resolve("inputId/file.xml/file.xml"),
                    baseDir.resolve("inputId/files/files.zip"));

            assertThat(FilesUtils.walk(baseDir)).containsExactlyInAnyOrder(
                    baseDir,
                    baseDir.resolve("inputId"),
                    baseDir.resolve("inputId/files"),
                    baseDir.resolve("inputId/file.xml"),
                    baseDir.resolve("inputId/file.xml/file.xml"),
                    baseDir.resolve("inputId/files/files.zip"));

            assertThat(StringUtils.readAsString(baseDir.resolve("inputId/file.xml/file.xml")))
                    .isEqualTo("test-file-content");
            assertThat(Files.readAllBytes(baseDir.resolve("inputId/files/files.zip")))
                    .isEqualTo(Files.readAllBytes(ZIP_RESOURCE_PATH));
        }
    }

    @TestPropertySource(properties = {
            "platform.inputdownloader.createSubdirectories=false"
    })
    public static class DownloaderServiceCreateSubdirectoriesFalseIT extends DownloaderServiceIT {

        @Test
        public void testDownloadInputs_StoresFilesAndZipInInputFolder_WhenCreateSubdirIsFalse() throws IOException {
            webServer.expect(request(HttpMethod.GET, "/download/file.xml"),
                    response(HttpStatus.OK, "test-file-content"));
            webServer.expect(request(HttpMethod.GET, "/download/files.zip"),
                    response(HttpStatus.OK, Files.readAllBytes(ZIP_RESOURCE_PATH)));

            Multimap<String, Path> downloadedInputs = downloaderService.downloadInputs(
                    baseDir,
                    ImmutableList.of(createParam(
                            INPUT_ID,
                            ImmutableList.of( mockServerUrl + "download/file.xml", mockServerUrl + "download/files.zip"))),
                    USER_UUID);

            assertThat(downloadedInputs.keySet()).hasSize(1);
            assertThat(downloadedInputs.get(INPUT_ID)).containsExactlyInAnyOrder(
                    baseDir.resolve("inputId/file.xml"),
                    baseDir.resolve("inputId/files.zip"));

            assertThat(FilesUtils.walk(baseDir)).containsExactlyInAnyOrder(
                    baseDir,
                    baseDir.resolve("inputId"),
                    baseDir.resolve("inputId/file.xml"),
                    baseDir.resolve("inputId/files.zip"));

            assertThat(StringUtils.readAsString(baseDir.resolve("inputId/file.xml")))
                    .isEqualTo("test-file-content");
            assertThat(Files.readAllBytes(baseDir.resolve("inputId/files.zip")))
                    .isEqualTo(Files.readAllBytes(ZIP_RESOURCE_PATH));
        }
    }

    protected K8sJobParams.DownloadableParam createParam(String inputName, List<String> values) {
        return K8sJobParams.DownloadableParam
                .builder()
                .paramName(inputName)
                .values(values)
                .build();
    }

    private String getMockServerUrl() {
        return "http://" + webServer.unwrap().getHostName() + ":" + webServer.unwrap().getPort() + "/";
    }

}
