package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HttpStacDownloaderTest {

    private static final Path STAC_BASE_TEST_PATH = Paths.get("src", "test", "resources", "stac");

    private static final int MOCKWEBSERVER_PORT = 8100;

    private static final int MAX_DOCUMENT_SIZE = 1024 * 100;

    private static final String STAC_DOCUMENT_PATH = "/api/stac-search-document.json";

    private static final ImmutableSet<String> EXPECTED_HEADERS =
            ImmutableSet.of("accept", "accept-encoding", "connection", "host", "user-agent");

    private HttpStacDownloader httpStacDownloader;

    private MockWebServerWrapper mockWebServerWrapper;

    @Before
    public void init() {
        mockWebServerWrapper = new MockWebServerWrapper(new MockWebServer());
        mockWebServerWrapper.start(MOCKWEBSERVER_PORT);

        DefaultStacInputsServiceProperties properties = new DefaultStacInputsServiceProperties();
        properties.setMaxDocumentSize(MAX_DOCUMENT_SIZE);

        httpStacDownloader = new HttpStacDownloader(new OkHttpClient(), new ObjectMapper(), properties);
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
    public void testSupports_ReturnsTrue_WhenProtocolIsHttp() throws Exception {
        assertThat(httpStacDownloader.supports(new URL("http://example.org/document.json"))).isTrue();
    }

    @Test
    public void testSupports_ReturnsTrue_WhenProtocolIsHttps() throws Exception {
        assertThat(httpStacDownloader.supports(new URL("https://example.org/document.json"))).isTrue();
    }

    @Test
    public void testSupports_ReturnsFalse_WhenProtocolIsNotHttpOrHttps() throws Exception {
        assertThat(httpStacDownloader.supports(new URL("ftp://example.org/document.json"))).isFalse();
    }

    @Test
    public void testDownload_ReturnsStacDocument_WhenResponseContainsStacDocument() throws Exception {
        String stacDocumentAsString = readAsString(STAC_BASE_TEST_PATH.resolve("http-stac-document.json"));
        mockWebServerWrapper.expect(
                request(HttpMethod.GET, STAC_DOCUMENT_PATH, "", EXPECTED_HEADERS),
                response(HttpStatus.OK, stacDocumentAsString));

        StacDocument stacDocument = httpStacDownloader.download(stacDocumentUrl(), "userId");

        assertThat(stacDocument.getFeatures()).hasSize(1);
        StacDocument.StacItem item = stacDocument.getFeatures().get(0);
        assertThat(item.getId()).isEqualTo("a1b2c3d4-0000-1111-2222-333344445555");
        assertThat(item.getStacVersion()).isEqualTo("1.0.0");
        assertThat(item.getAssets()).containsOnlyKeys("data");
        assertThat(item.getAssets().get("data").getHref())
                .isEqualTo(new URI("https://example.org/assets/sample-scene.tif"));
    }

    @Test
    public void testDownload_ThrowsIllegalStateException_WhenResponseStatusIsNotFound() {
        mockWebServerWrapper.expect(
                request(HttpMethod.GET, STAC_DOCUMENT_PATH, "", EXPECTED_HEADERS),
                response(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> httpStacDownloader.download(stacDocumentUrl(), "userId"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Response returned with unexpected status code 404");
    }

    @Test
    public void testDownload_ThrowsIllegalStateException_WhenDocumentExceedsMaximumSize() {
        mockWebServerWrapper.expect(
                request(HttpMethod.GET, STAC_DOCUMENT_PATH, "", EXPECTED_HEADERS),
                response(HttpStatus.OK, new String(new byte[MAX_DOCUMENT_SIZE + 2])));

        assertThatThrownBy(() -> httpStacDownloader.download(stacDocumentUrl(), "userId"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Response exceeds maximum expected size " + (MAX_DOCUMENT_SIZE + 1));
    }

    @Test
    public void testDownload_ThrowsIllegalStateException_WhenResponseBodyIsNotValidStacDocument() {
        mockWebServerWrapper.expect(
                request(HttpMethod.GET, STAC_DOCUMENT_PATH, "", EXPECTED_HEADERS),
                response(HttpStatus.OK, "this is not valid json"));

        assertThatThrownBy(() -> httpStacDownloader.download(stacDocumentUrl(), "userId"))
                .isInstanceOf(IllegalStateException.class);
    }

    private URL stacDocumentUrl() {
        return mockWebServerWrapper.unwrap().url(STAC_DOCUMENT_PATH).url();
    }
}