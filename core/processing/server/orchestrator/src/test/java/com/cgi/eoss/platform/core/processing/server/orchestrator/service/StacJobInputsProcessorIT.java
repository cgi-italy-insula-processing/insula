package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class StacJobInputsProcessorIT {

    private static final Path STAC_BASE_TEST_PATH = Paths.get("src", "test", "resources", "stac");

    @Autowired
    private JobInputsProcessor jobInputsProcessor;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${platform.orchestrator.mockserver.port}")
    protected Integer mockServerPort;

    private MockWebServerWrapper webServer;

    @Before
    public void setUp() {
        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(mockServerPort);
    }

    @After
    public void tearDown() {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }
    }

    @Test
    public void testJobInputsProcessor_InstantiatesStacJobInputsProcessor_WhenServerAppPropertyIsProcessingCore() {
        assertThat(applicationContext.getBeanNamesForType(JobInputsProcessor.class)).hasSize(1);
        assertThat(applicationContext.getBean(JobInputsProcessor.class)).isInstanceOf(StacJobInputsProcessor.class);
    }

    @Test
    public void testSplitAndExplodeInputs_ExplodesOnlyInputSelectedByKeyAndLeavesOthersUntouched_WhenSelectedInputIsStac() throws Exception {

        {
            // Mock download Stac Document response
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET,
                            "/api/stac-search-document-multiple-items.json", "",
                            ImmutableSet.of("accept", "accept-encoding", "connection", "host", "user-agent")),
                    MockWebServerWrapper.response(HttpStatus.OK,
                            readAsString(STAC_BASE_TEST_PATH.resolve("stac-search-document-multiple-items.json"))));
        }

        {
            // Mock S3 does bucket exist
            webServer.expect(MockWebServerWrapper.request(HttpMethod.HEAD, "/s3/stac-items/"),
                    MockWebServerWrapper.response(HttpStatus.OK));

            // Mock S3 store document in bucket
            webServer.expect(MockWebServerWrapper.requestCustomBodyTest(HttpMethod.PUT,
                            "/s3/stac-items/jobId/stacInput/catalog.json",
                            (recordedRequest) ->
                                    verifyBodyContainsCatalog(recordedRequest,
                                            readAsString(STAC_BASE_TEST_PATH.resolve("stac-catalog-multiple-items.json"))
                                    )),
                    MockWebServerWrapper.response(HttpStatus.OK));
        }

        String mockWebServerLocation = "http://".concat(webServer.unwrap().getHostName().concat(":" + mockServerPort));

        JobInput stacInput = JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)
                .values(Collections.singletonList(mockWebServerLocation.concat("/api/stac-search-document-multiple-items.json")))
                .build();
        JobInput otherInput = JobInput.builder()
                .id("otherInput")
                .type(JobInput.Type.URL)
                .values(ImmutableList.of("input1", "input2"))
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .jobId("jobId")
                .inputs(ImmutableMap.of("stacInput", stacInput, "otherInput", otherInput))
                .build();

        JobInput jobInputExploded = jobInputsProcessor.splitAndExplodeInputs(jobInputs, "stacInput");

        assertThat(jobInputExploded.getId()).isEqualTo("stacInput");
        assertThat(jobInputExploded.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(jobInputExploded.getValues()).containsExactlyInAnyOrder(
                mockWebServerLocation.concat("/api/stac-search-document-multiple-items.json#9106a7fe-9d4c-5673-9979-0de28035ea7f"),
                mockWebServerLocation.concat("/api/stac-search-document-multiple-items.json#7016x7fr-9d4c-5672-1979-0dy48025ec8f")
        );
        assertThat(jobInputExploded.getInternalReference()).isEqualTo(
                new URL(mockWebServerLocation.concat("/s3/stac-items/jobId/stacInput/catalog.json")));
        List<StacDocument.StacItem> expectedStacItems = jobInputExploded.getContentsAsStac();
        assertThat(expectedStacItems).hasSize(2);
        assertThat(expectedStacItems.get(0).getId()).isEqualTo("9106a7fe-9d4c-5673-9979-0de28035ea7f");
        assertThat(expectedStacItems.get(0).getStacVersion()).isEqualTo("1.0.0");
        assertThat(expectedStacItems.get(1).getId()).isEqualTo("7016x7fr-9d4c-5672-1979-0dy48025ec8f");
        assertThat(expectedStacItems.get(1).getStacVersion()).isEqualTo("1.0.0");
        Map<String, StacDocument.Asset> featureOneAssets = expectedStacItems.get(0).getAssets();
        assertThat(featureOneAssets).hasSize(1);
        assertThat(featureOneAssets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-1.tif"));
        Map<String, StacDocument.Asset> featureTwoAssets = expectedStacItems.get(1).getAssets();
        assertThat(featureTwoAssets).hasSize(1);
        assertThat(featureTwoAssets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-2.tif"));
    }

    @Test
    public void testExplodeInputs_ExplodesEveryStacInputAndLeavesNonStacInputsUntouched_WhenInputsContainStacAndNonStacInputs() throws Exception {

        {
            // Mock download Stac Document response
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET,
                            "/api/stac-search-document-multiple-items.json", "",
                            ImmutableSet.of("accept", "accept-encoding", "connection", "host", "user-agent")),
                    MockWebServerWrapper.response(HttpStatus.OK,
                            readAsString(STAC_BASE_TEST_PATH.resolve("stac-search-document-multiple-items.json"))));
        }

        {
            // Mock S3 does bucket exist
            webServer.expect(MockWebServerWrapper.request(HttpMethod.HEAD, "/s3/stac-items/"),
                    MockWebServerWrapper.response(HttpStatus.OK));

            // Mock S3 store document in bucket
            webServer.expect(MockWebServerWrapper.requestCustomBodyTest(HttpMethod.PUT,
                            "/s3/stac-items/jobId/stacInput/catalog.json",
                            (recordedRequest) ->
                                    verifyBodyContainsCatalog(recordedRequest,
                                            readAsString(STAC_BASE_TEST_PATH.resolve("stac-catalog-multiple-items.json"))
                                    )),
                    MockWebServerWrapper.response(HttpStatus.OK));
        }

        String mockWebServerLocation = "http://".concat(webServer.unwrap().getHostName().concat(":" + mockServerPort));

        JobInput stacInput = JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)
                .values(Collections.singletonList(mockWebServerLocation.concat("/api/stac-search-document-multiple-items.json")))
                .build();
        JobInput nonStacInput = JobInput.builder()
                .id("nonStacInput")
                .type(JobInput.Type.URL)
                .values(ImmutableList.of("input1", "input2"))
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .jobId("jobId")
                .inputs(ImmutableMap.of("stacInput", stacInput, "nonStacInput", nonStacInput))
                .build();

        JobInputs jobInputsExploded = jobInputsProcessor.explodeInputs(jobInputs);

        assertThat(jobInputsExploded.getJobId()).isEqualTo("jobId");
        assertThat(jobInputsExploded.getInputs()).hasSize(2);

        JobInput stacInputExploded = jobInputsExploded.getInputs().get("stacInput");
        assertThat(stacInputExploded.getId()).isEqualTo("stacInput");
        assertThat(stacInputExploded.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(stacInputExploded.getValues()).containsExactlyInAnyOrder(
                mockWebServerLocation.concat("/api/stac-search-document-multiple-items.json#9106a7fe-9d4c-5673-9979-0de28035ea7f"),
                mockWebServerLocation.concat("/api/stac-search-document-multiple-items.json#7016x7fr-9d4c-5672-1979-0dy48025ec8f")
        );
        assertThat(stacInputExploded.getInternalReference()).isEqualTo(
                new URL(mockWebServerLocation.concat("/s3/stac-items/jobId/stacInput/catalog.json")));
        List<StacDocument.StacItem> expectedStacItems = stacInputExploded.getContentsAsStac();
        assertThat(expectedStacItems).hasSize(2);
        assertThat(expectedStacItems.get(0).getId()).isEqualTo("9106a7fe-9d4c-5673-9979-0de28035ea7f");
        assertThat(expectedStacItems.get(0).getStacVersion()).isEqualTo("1.0.0");
        assertThat(expectedStacItems.get(1).getId()).isEqualTo("7016x7fr-9d4c-5672-1979-0dy48025ec8f");
        assertThat(expectedStacItems.get(1).getStacVersion()).isEqualTo("1.0.0");
        Map<String, StacDocument.Asset> featureOneAssets = expectedStacItems.get(0).getAssets();
        assertThat(featureOneAssets).hasSize(1);
        assertThat(featureOneAssets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-1.tif"));
        Map<String, StacDocument.Asset> featureTwoAssets = expectedStacItems.get(1).getAssets();
        assertThat(featureTwoAssets).hasSize(1);
        assertThat(featureTwoAssets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-2.tif"));

        JobInput nonStacInputResult = jobInputsExploded.getInputs().get("nonStacInput");
        assertThat(nonStacInputResult).isEqualTo(nonStacInput);
    }

    private static void verifyBodyContainsCatalog(RecordedRequest recordedRequest, String catalog) {
        assertThat(recordedRequest.getBody().readUtf8()).containsIgnoringWhitespaces(catalog);
    }
}
