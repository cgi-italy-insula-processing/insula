package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInput;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableSet;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class StacInputsServiceIT {

    private static final Path STAC_BASE_TEST_PATH = Paths.get("src", "test", "resources", "stac");

    @Autowired
    private StacInputsService stacInputsService;

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
    public void testExplodeStacItems_ReturnsJobInputWithExplodedStacItemsAndInternalReferenceAndContents() throws Exception {
        String stacDocumentAsString = readAsString(STAC_BASE_TEST_PATH.resolve("stac-search-document.json"));

        {
            // Mock download Stac Document response
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET,
                            "/api/stac-search-document.json", "",
                            ImmutableSet.of("accept", "accept-encoding", "connection", "host", "user-agent")),
                    MockWebServerWrapper.response(HttpStatus.OK, stacDocumentAsString));
        }

        {
            // Mock S3 does bucket exist
            webServer.expect(MockWebServerWrapper.request(HttpMethod.HEAD, "/s3/stac-items/"),
                    MockWebServerWrapper.response(HttpStatus.OK));

            // Mock S3 store document in bucket
            webServer.expect(MockWebServerWrapper.requestWithOutBodyTest(HttpMethod.PUT,
                            "/s3/stac-items/jobId/stacInput/catalog.json"),
                    MockWebServerWrapper.response(HttpStatus.OK));
        }

        String mockWebServerLocation = "http://".concat(webServer.unwrap().getHostName().concat(":" + mockServerPort));
        JobInput jobInput = JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)
                .values(Collections.singletonList(mockWebServerLocation.concat("/api/stac-search-document.json")))
                .build();

        JobInput jobInputExploded = stacInputsService.explodeStacItems(jobInput, "jobId", "userName");

        // Assert exploded job input
        assertThat(jobInputExploded.getId()).isEqualTo("stacInput");
        assertThat(jobInputExploded.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(jobInputExploded.getValues()).isEqualTo(Collections.singletonList(
                mockWebServerLocation.concat("/api/stac-search-document.json#9106a7fe-9d4c-5673-9979-0de28035ea7f")
        ));
        assertThat(jobInputExploded.getInternalReference()).isEqualTo(
                new URL(mockWebServerLocation.concat("/s3/stac-items/jobId/stacInput/catalog.json")));
        List<StacDocument.StacItem> expectedStacItems = jobInputExploded.getContentsAsStac();
        assertThat(expectedStacItems).hasSize(1);
        assertThat(expectedStacItems.get(0).getId()).isEqualTo("9106a7fe-9d4c-5673-9979-0de28035ea7f");
        assertThat(expectedStacItems.get(0).getStacVersion()).isEqualTo("1.0.0");
        Map<String, StacDocument.Asset> featureAssets = expectedStacItems.get(0).getAssets();
        assertThat(featureAssets).hasSize(1);
        assertThat(featureAssets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-1.tif"));
    }
}
