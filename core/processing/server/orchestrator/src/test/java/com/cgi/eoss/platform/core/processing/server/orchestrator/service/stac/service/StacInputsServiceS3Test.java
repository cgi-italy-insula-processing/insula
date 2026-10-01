package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInput;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.requestCustomBodyTest;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StacInputsServiceS3Test {

    private static final Path STAC_BASE_TEST_PATH = Paths.get("src", "test", "resources", "stac");

    private static final int MOCKWEBSERVER_PORT = 8100;

    private static final String BUCKET_NAME = "stac-items-bucket";

    private static final String USER_NAME = "userId";

    private static final String DOWNLOAD_PATH = "/api/stac-search-document.json";

    private static final String MULTI_ITEM_DOWNLOAD_PATH = "/api/stac-search-document-multiple-items.json";

    private static final String FEATURE_ID = "9106a7fe-9d4c-5673-9979-0de28035ea7f";

    private static final ImmutableSet<String> DOWNLOAD_HEADERS =
            ImmutableSet.of("accept", "accept-encoding", "connection", "host", "user-agent");

    private MockWebServerWrapper mockWebServerWrapper;

    private String mockWebServerLocation;

    private StacInputsService stacInputsService;

    @Before
    public void init() {
        mockWebServerWrapper = new MockWebServerWrapper(new MockWebServer());
        mockWebServerWrapper.start(MOCKWEBSERVER_PORT);
        mockWebServerLocation = "http://" + mockWebServerWrapper.unwrap().getHostName() + ":" + MOCKWEBSERVER_PORT;
        ObjectMapper objectMapper = new ObjectMapper();
        StacDownloader httpDownloader = new HttpStacDownloader(
                new OkHttpClient(), objectMapper, new DefaultStacInputsServiceProperties());
        stacInputsService = new StacInputsService(objectMapper, buildS3Client(mockWebServerLocation),
                new DefaultStacItemsS3Bucket(BUCKET_NAME), ImmutableList.of(httpDownloader));
    }

    @After
    public void shutdown() {
        try {
            mockWebServerWrapper.verifyExpectedRequests();
        } finally {
            mockWebServerWrapper.shutdown();
        }
    }

    @Test
    public void testExplodeStacItems_ReturnsJobInputWithExplodedStacItemsAndInternalReferenceAndContents() throws Exception {
        expectDownload(DOWNLOAD_PATH, "stac-search-document.json");
        mockWebServerWrapper.expect(request(HttpMethod.HEAD, "/" + BUCKET_NAME + "/"), response(HttpStatus.OK));
        expectCatalogStored("stac-catalog.json");

        JobInput exploded = stacInputsService.explodeStacItems(createDownloadableStacJobInput(DOWNLOAD_PATH), "jobId", USER_NAME);

        assertThat(exploded.getId()).isEqualTo("stacInput");
        assertThat(exploded.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(exploded.getValues()).isEqualTo(ImmutableList.of(
                mockWebServerLocation.concat(DOWNLOAD_PATH + "#" + FEATURE_ID)));
        assertThat(exploded.getInternalReference()).isEqualTo(
                new URL(mockWebServerLocation.concat("/" + BUCKET_NAME + "/jobId/stacInput/catalog.json")));
        List<StacDocument.StacItem> stacItems = exploded.getContentsAsStac();
        assertThat(stacItems).hasSize(1);
        assertThat(stacItems.get(0).getId()).isEqualTo(FEATURE_ID);
        assertThat(stacItems.get(0).getStacVersion()).isEqualTo("1.0.0");
        Map<String, StacDocument.Asset> assets = stacItems.get(0).getAssets();
        assertThat(assets).hasSize(1);
        assertThat(assets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-1.tif"));
    }

    @Test
    public void testExplodeStacItems_ReturnsJobInputWithExplodedAndSplitStacItemsAndInternalReferenceAndContents_WhenStacDocumentContainsMultipleStacItems() throws Exception {
        expectDownload(MULTI_ITEM_DOWNLOAD_PATH, "stac-search-document-multiple-items.json");
        mockWebServerWrapper.expect(request(HttpMethod.HEAD, "/" + BUCKET_NAME + "/"), response(HttpStatus.OK));
        expectCatalogStored("stac-catalog-multiple-items.json");

        JobInput exploded = stacInputsService.explodeStacItems(
                createDownloadableStacJobInput(MULTI_ITEM_DOWNLOAD_PATH), "jobId", USER_NAME);

        assertThat(exploded.getValues()).containsExactlyInAnyOrder(
                mockWebServerLocation.concat(MULTI_ITEM_DOWNLOAD_PATH + "#9106a7fe-9d4c-5673-9979-0de28035ea7f"),
                mockWebServerLocation.concat(MULTI_ITEM_DOWNLOAD_PATH + "#7016x7fr-9d4c-5672-1979-0dy48025ec8f"));
        List<StacDocument.StacItem> stacItems = exploded.getContentsAsStac();
        assertThat(stacItems).hasSize(2);
        assertThat(stacItems.get(0).getId()).isEqualTo("9106a7fe-9d4c-5673-9979-0de28035ea7f");
        assertThat(stacItems.get(0).getStacVersion()).isEqualTo("1.0.0");
        assertThat(stacItems.get(1).getId()).isEqualTo("7016x7fr-9d4c-5672-1979-0dy48025ec8f");
        assertThat(stacItems.get(1).getStacVersion()).isEqualTo("1.0.0");
        assertThat(stacItems.get(0).getAssets().get("internal").getHref())
                .isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-1.tif"));
        assertThat(stacItems.get(1).getAssets().get("internal").getHref())
                .isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-2.tif"));
    }

    @Test
    public void testExplodeStacItems_CreatesBucketAndReturnsJobInputWithExplodedStacItemsAndInternalReferenceAndContents_WhenBucketDoesNotExist() throws Exception {
        expectDownload(DOWNLOAD_PATH, "stac-search-document.json");
        mockWebServerWrapper.expect(request(HttpMethod.HEAD, "/" + BUCKET_NAME + "/"), response(HttpStatus.NOT_FOUND));
        mockWebServerWrapper.expect(request(HttpMethod.PUT, "/" + BUCKET_NAME + "/"), response(HttpStatus.CREATED));
        expectCatalogStored("stac-catalog.json");

        JobInput exploded = stacInputsService.explodeStacItems(createDownloadableStacJobInput(DOWNLOAD_PATH), "jobId", USER_NAME);

        assertThat(exploded.getId()).isEqualTo("stacInput");
        assertThat(exploded.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(exploded.getValues()).isEqualTo(ImmutableList.of(
                mockWebServerLocation.concat(DOWNLOAD_PATH + "#" + FEATURE_ID)));
        assertThat(exploded.getInternalReference()).isEqualTo(
                new URL(mockWebServerLocation.concat("/" + BUCKET_NAME + "/jobId/stacInput/catalog.json")));
        List<StacDocument.StacItem> stacItems = exploded.getContentsAsStac();
        assertThat(stacItems).hasSize(1);
        assertThat(stacItems.get(0).getId()).isEqualTo(FEATURE_ID);
        assertThat(stacItems.get(0).getStacVersion()).isEqualTo("1.0.0");
        Map<String, StacDocument.Asset> assets = stacItems.get(0).getAssets();
        assertThat(assets).hasSize(1);
        assertThat(assets.get("internal").getHref()).isEqualTo(new URI("eopaas://refData/11/SampleGeotiff-1.tif"));
    }

    @Test
    public void testExplodeStacItems_ThrowsIllegalStateException_WhenHeadBucketResponseStatusIsUnauthorized() throws Exception {
        expectDownload(DOWNLOAD_PATH, "stac-search-document.json");
        mockWebServerWrapper.expect(request(HttpMethod.HEAD, "/" + BUCKET_NAME + "/"), response(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> stacInputsService.explodeStacItems(createDownloadableStacJobInput(DOWNLOAD_PATH), "jobId", USER_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client Error (Service: Amazon S3; Status Code: 401; Error Code: 401");
    }

    @Test
    public void testExplodeStacItems_ThrowsIllegalStateException_WhenBucketCreationResponseStatusIsUnauthorized() throws Exception {
        expectDownload(DOWNLOAD_PATH, "stac-search-document.json");
        mockWebServerWrapper.expect(request(HttpMethod.HEAD, "/" + BUCKET_NAME + "/"), response(HttpStatus.NOT_FOUND));
        mockWebServerWrapper.expect(request(HttpMethod.PUT, "/" + BUCKET_NAME + "/"), response(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> stacInputsService.explodeStacItems(createDownloadableStacJobInput(DOWNLOAD_PATH), "jobId", USER_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client Error (Service: Amazon S3; Status Code: 401; Error Code: 401");
    }

    @Test
    public void testExplodeStacItems_ThrowsIllegalStateException_WhenPutObjectResponseIsUnauthorized() throws Exception {
        expectDownload(DOWNLOAD_PATH, "stac-search-document.json");
        mockWebServerWrapper.expect(request(HttpMethod.HEAD, "/" + BUCKET_NAME + "/"), response(HttpStatus.OK));
        mockWebServerWrapper.expect(requestCustomBodyTest(HttpMethod.PUT,
                        "/" + BUCKET_NAME + "/jobId/stacInput/catalog.json",
                        recordedRequest -> verifyBodyContainsCatalog(recordedRequest,
                                readAsString(STAC_BASE_TEST_PATH.resolve("stac-catalog.json")))),
                response(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> stacInputsService.explodeStacItems(createDownloadableStacJobInput(DOWNLOAD_PATH), "jobId", USER_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client Error (Service: Amazon S3; Status Code: 401; Error Code: 401");
    }

    private JobInput createDownloadableStacJobInput(String path) {
        return JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of(mockWebServerLocation.concat(path)))
                .build();
    }

    private void expectDownload(String path, String documentFileName) {
        mockWebServerWrapper.expect(
                request(HttpMethod.GET, path, "", DOWNLOAD_HEADERS),
                response(HttpStatus.OK, readAsString(STAC_BASE_TEST_PATH.resolve(documentFileName))));
    }

    private void expectCatalogStored(String catalogFileName) {
        mockWebServerWrapper.expect(requestCustomBodyTest(HttpMethod.PUT,
                        "/" + BUCKET_NAME + "/jobId/stacInput/catalog.json",
                        recordedRequest -> verifyBodyContainsCatalog(recordedRequest,
                                readAsString(STAC_BASE_TEST_PATH.resolve(catalogFileName)))),
                response(HttpStatus.OK));
    }

    private static AmazonS3 buildS3Client(String s3endpoint) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");
        clientConfiguration.setMaxErrorRetry(1);
        clientConfiguration.setConnectionTimeout(10000);
        clientConfiguration.setSocketTimeout(10000);
        clientConfiguration.setProtocol(Protocol.HTTP);
        return AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3endpoint, "us-east-1"))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("access", "secret")))
                .build();
    }

    private static void verifyBodyContainsCatalog(RecordedRequest recordedRequest, String catalog) {
        assertThat(recordedRequest.getBody().readUtf8()).containsIgnoringWhitespaces(catalog);
    }
}