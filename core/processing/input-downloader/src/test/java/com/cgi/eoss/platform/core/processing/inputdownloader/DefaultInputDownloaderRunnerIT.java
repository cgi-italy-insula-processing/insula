package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.core.StringUtils;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.cgi.eoss.platform.core.processing.inputdownloader.EnvironmentService.INPUT_DIR;
import static com.cgi.eoss.platform.core.processing.inputdownloader.EnvironmentService.JOB_CONFIG_FILENAME;
import static com.cgi.eoss.platform.core.processing.inputdownloader.EnvironmentService.JOB_INPUTS_JSON_FILENAME;
import static com.cgi.eoss.platform.core.processing.inputdownloader.EnvironmentService.LEGACY_JOB_CONFIG_FILENAMES;
import static com.cgi.eoss.platform.core.processing.inputdownloader.EnvironmentService.OUTPUT_DIR;
import static com.cgi.eoss.platform.core.processing.inputdownloader.EnvironmentService.PERSISTENT_DIR;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;



@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {InputDownloaderCoreTestConfig.class})
@TestPropertySource(
        locations = {"classpath:test-input-downloader-core.properties"},
        properties = {
                "inputs={\"urlParam\": {\"type\": \"URL\", \"values\": [\"http://localhost:${platform.mockserver.port}/urlInput/00.xml\"]}}",
                "job_id=10",
                "job_owner=jobOwner"
        })
public class DefaultInputDownloaderRunnerIT {

    @Autowired
    private DefaultInputDownloaderRunner inputDownloaderRunner;

    @Autowired
    private Path basePath;

    @Value("${platform.mockserver.port}")
    protected int mockServerPort;

    protected final MockWebServerWrapper webServer = new MockWebServerWrapper(new MockWebServer());


    @Before
    public void init() {
        webServer.start(mockServerPort);
        cleanupDirContents();
    }

    @After
    public void shutdown() {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
            cleanupDirContents();
        }
    }

    @Test
    public void testRun_CreatesJobEnvironment() throws Exception {
        String inputId = "urlParam";
        String inputValue = "http://localhost:" + mockServerPort + "/urlInput/00.xml";

        webServer.expect(
                request(HttpMethod.GET, "/urlInput/00.xml"),
                response(HttpStatus.OK, "url-input-body.xml")
        );

        inputDownloaderRunner.run();

        assertThat(FilesUtils.list(basePath)).hasSize(6);

        assertThat(FilesUtils.list(basePath.resolve(OUTPUT_DIR))).isEmpty();
        assertThat(FilesUtils.list(basePath.resolve(PERSISTENT_DIR))).isEmpty();

        String expectedConfigFileContent = inputId + "=\"" + inputValue + "\"";
        assertThat(Files.readAllLines(basePath.resolve(JOB_CONFIG_FILENAME)))
                .containsExactly(expectedConfigFileContent);
        assertThat(Files.readAllLines(basePath.resolve(LEGACY_JOB_CONFIG_FILENAMES.get(0))))
                .containsExactly(expectedConfigFileContent);

        assertThat(FilesUtils.list(basePath.resolve(INPUT_DIR).resolve(inputId))).hasSize(1);
        assertThat(StringUtils.readAsString(basePath.resolve(INPUT_DIR).resolve(inputId).resolve("00.xml")))
                .isEqualTo("url-input-body.xml");

        JSONAssert.assertEquals(String.format("{\"%s\": [\"%s\"]}", inputId, inputValue),
                StringUtils.readAsString(basePath.resolve(JOB_INPUTS_JSON_FILENAME)),
                JSONCompareMode.STRICT);

    }

    private void cleanupDirContents() {
        FilesUtils.deleteDirContentsIfExists(basePath);
    }
}