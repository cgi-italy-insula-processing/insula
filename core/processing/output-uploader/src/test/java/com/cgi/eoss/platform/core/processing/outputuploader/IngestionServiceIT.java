package com.cgi.eoss.platform.core.processing.outputuploader;

import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableMap;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.requestCustomBodyTest;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = {OutputUploaderCoreTestConfig.class})
@TestPropertySource(locations = {"classpath:test-output-uploader-core.properties"})
public class IngestionServiceIT {

    private static final Path BASE_PATH = Paths.get("src", "test", "resources");

    @Autowired
    private IngestionService ingestionService;

    @Value("${platform.output.uploader.mockserver.port}")
    private Integer webServerPort;

    private MockWebServerWrapper webServer;

    @Before
    public void init() {
        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(webServerPort);
    }

    @After
    public void shutdown() {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }
    }

    @Test
    public void testIngestOutputToExistingBucket() throws Exception {
        Path outputDir = BASE_PATH.resolve("outputId");
        String jobId = "jobId";

        Map<String, String> map = new HashMap<>();
        map.put("/s3/job-outputs/jobId/productOne", "11;chunk-signature=[0-9a-f]+\\RproductOneContent\\R0;chunk-signature=[0-9a-f]+\\R\\R");
        map.put("/s3/job-outputs/jobId/productTwo", "11;chunk-signature=[0-9a-f]+\\RproductTwoContent\\R0;chunk-signature=[0-9a-f]+\\R\\R");

        // doesBucketExist
        webServer.expect(request(HttpMethod.HEAD, "/s3/job-outputs/"), response(HttpStatus.OK));

        Pattern pathPattern = Pattern.compile("/s3/job-outputs/jobId/product(One|Two)");
        // putObject: first product
        webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                    assertThat(map).containsKey(r.getPath());
                    assertThat(r.getBody().readUtf8()).matches(map.remove(r.getPath()));
                }),
                response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));

        // putObject: second product
        webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                    assertThat(map).containsKey(r.getPath());
                    assertThat(r.getBody().readUtf8()).matches(map.remove(r.getPath()));
                }),
                response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));

        ingestionService.uploadOutputs(outputDir, jobId, new HashMap<>());

    }

    @Test
    public void testIngestOutputToNotExistingBucket() throws Exception {

        Path outputDir = BASE_PATH.resolve("outputId");
        String jobId = "jobId";
        Map<String, String> map = new HashMap<>();
        map.put("/s3/job-outputs/jobId/productOne", "11;chunk-signature=[0-9a-f]+\\RproductOneContent\\R0;chunk-signature=[0-9a-f]+\\R\\R");
        map.put("/s3/job-outputs/jobId/productTwo", "11;chunk-signature=[0-9a-f]+\\RproductTwoContent\\R0;chunk-signature=[0-9a-f]+\\R\\R");

        // doesBucketExist
        webServer.expect(request(HttpMethod.HEAD, "/s3/job-outputs/"), response(HttpStatus.NOT_FOUND));

        // createBucket
        webServer.expect(request(HttpMethod.PUT, "/s3/job-outputs/"), response(HttpStatus.CREATED));

        Pattern pathPattern = Pattern.compile("/s3/job-outputs/jobId/product(One|Two)");
        // putObject: first product
        webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                    assertThat(map).containsKey(r.getPath());
                    assertThat(r.getBody().readUtf8()).matches(map.remove(r.getPath()));
                }),
                response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));

        // putObject: second product
        webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                    assertThat(map).containsKey(r.getPath());
                    assertThat(r.getBody().readUtf8()).matches(map.remove(r.getPath()));
                }),
                response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));

        ingestionService.uploadOutputs(outputDir, jobId, new HashMap<>());

    }

    @Test
    public void testIngestOutput_ThrowsIOException_WhenUploadFails() throws Exception {

        Path outputDir = BASE_PATH.resolve("outputId");
        String jobId = "jobId";
        Map<String, String> map = new HashMap<>();
        map.put("/s3/job-outputs/jobId/productOne", "11;chunk-signature=[0-9a-f]+\\RproductOneContent\\R0;chunk-signature=[0-9a-f]+\\R\\R");
        map.put("/s3/job-outputs/jobId/productTwo", "11;chunk-signature=[0-9a-f]+\\RproductTwoContent\\R0;chunk-signature=[0-9a-f]+\\R\\R");

        // doesBucketExist
        webServer.expect(request(HttpMethod.HEAD, "/s3/job-outputs/"), response(HttpStatus.OK));

        Pattern pathPattern = Pattern.compile("/s3/job-outputs/jobId/product(One|Two)");
        // putObject: first product
        webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                    assertThat(map).containsKey(r.getPath());
                    assertThat(r.getBody().readUtf8()).matches(map.remove(r.getPath()));
                }),
                response(HttpStatus.BAD_REQUEST, SocketPolicy.EXPECT_CONTINUE));

        // putObject: second product
        webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                    assertThat(map).containsKey(r.getPath());
                    assertThat(r.getBody().readUtf8()).matches(map.remove(r.getPath()));
                }),
                response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));
        try {
            ingestionService.uploadOutputs(outputDir, jobId,new HashMap<>());
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage()).contains("Failed to upload files for job jobId");
        }
    }



    @Test
    public void testUploadOutputs_ArchivesAndUploadsOutputsToBeStagedOut_WhenJobOutputsMapIsProvided() throws Exception {
        String jobId = "jobId";

        Path tempDir = Files.createTempDirectory("stage-out-");
        Path catalogDir = tempDir.resolve("catalog-dir");
        Files.createDirectories(catalogDir);

        Files.copy(BASE_PATH.resolve("stage-out/catalog.json"), catalogDir.resolve("catalog.json"));
        Files.copy(BASE_PATH.resolve("stage-out/item.json"), catalogDir.resolve("item.json"));
        Files.copy(BASE_PATH.resolve("stage-out/test-file-1.txt"), catalogDir.resolve("test-file-1.txt"));
        Files.copy(BASE_PATH.resolve("stage-out/test-file-2.txt"), catalogDir.resolve("test-file-2.txt"));

        Path secondOutputCatalogDir = tempDir.resolve("second-catalog-dir");
        Files.createDirectories(secondOutputCatalogDir);
        Files.copy(BASE_PATH.resolve("stage-out/catalog-multi.json"), secondOutputCatalogDir.resolve("catalog.json"));
        Files.copy(BASE_PATH.resolve("stage-out/item.json"), secondOutputCatalogDir.resolve("item.json"));
        Files.copy(BASE_PATH.resolve("stage-out/item2.json"), secondOutputCatalogDir.resolve("item2.json"));
        Files.copy(BASE_PATH.resolve("stage-out/test-file-1.txt"), secondOutputCatalogDir.resolve("test-file-1.txt"));
        Files.copy(BASE_PATH.resolve("stage-out/test-file-2.txt"), secondOutputCatalogDir.resolve("test-file-2.txt"));

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("/s3/job-outputs/jobId/outputId/test-item.zip",
                "\\d[a-zA-Z]\\d;chunk-signature=[0-9a-f]+\\R([.\\S\\s])+0;chunk-signature=[0-9a-f]+\\R\\R");
        bodyMap.put("/s3/job-outputs/jobId/secondOutput/test-item.zip",
                "\\d[a-zA-Z]\\d;chunk-signature=[0-9a-f]+\\R([.\\S\\s])+0;chunk-signature=[0-9a-f]+\\R\\R");
        bodyMap.put("/s3/job-outputs/jobId/secondOutput/test-item-2.zip",
                "\\d\\d\\d;chunk-signature=[0-9a-f]+\\R([.\\S\\s])+0;chunk-signature=[0-9a-f]+\\R\\R");

        Map<String, List<String>> filesMap = new HashMap<>();
        filesMap.put("/s3/job-outputs/jobId/outputId/test-item.zip", Arrays.asList(
                "test-file-1.txt", "test-file-2.txt", "item.json"
        ));
        filesMap.put("/s3/job-outputs/jobId/secondOutput/test-item.zip", Arrays.asList(
                "test-file-1.txt", "test-file-2.txt", "item.json"
        ));
        filesMap.put("/s3/job-outputs/jobId/secondOutput/test-item-2.zip", Arrays.asList(
                "test-file-1.txt", "item2.json"
        ));

        {
            // doesBucketExist
            webServer.expect(request(HttpMethod.HEAD, "/s3/job-outputs/"), response(HttpStatus.OK));

            Pattern pathPattern = Pattern.compile("/s3/job-outputs/jobId/(outputId|secondOutput)/test-item(-2)?.zip");
            webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                assertThat(bodyMap).containsKey(r.getPath());
                String body = r.getBody().readUtf8();
                assertThat(body).matches(bodyMap.remove(r.getPath()));
                assertThat(body).contains(filesMap.remove(r.getPath()));
            }), response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));

            webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                assertThat(bodyMap).containsKey(r.getPath());
                String body = r.getBody().readUtf8();
                assertThat(body).matches(bodyMap.remove(r.getPath()));
                assertThat(body).contains(filesMap.remove(r.getPath()));
            }), response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));


            webServer.expect(requestCustomBodyTest(HttpMethod.PUT, pathPattern, r -> {
                assertThat(bodyMap).containsKey(r.getPath());
                String body = r.getBody().readUtf8();
                assertThat(body).matches(bodyMap.remove(r.getPath()));
                assertThat(body).contains(filesMap.remove(r.getPath()));
            }), response(HttpStatus.OK, SocketPolicy.EXPECT_CONTINUE));
        }

        String basePathAsString = tempDir.toString();
        ingestionService.uploadOutputs(tempDir, jobId,
                ImmutableMap.of("outputId", basePathAsString.concat("/catalog-dir"),
                        "secondOutput", basePathAsString.concat("/second-catalog-dir")));
    }
}
