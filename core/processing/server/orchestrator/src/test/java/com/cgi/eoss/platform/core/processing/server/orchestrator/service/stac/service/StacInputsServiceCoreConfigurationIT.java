package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
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

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class StacInputsServiceCoreConfigurationIT {

    @Autowired
    protected ApplicationContext applicationContext;

    @Value("${platform.orchestrator.mockserver.port}")
    protected Integer mockServerPort;

    private MockWebServerWrapper webServer;

    @Before
    public void init() {
        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(mockServerPort);
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
    public void testStacInputsServiceCoreConfiguration_CreatesStacClientBean() {
        assertThat(applicationContext.getBean("stacClient")).isInstanceOf(OkHttpClient.class);
    }

    @Test
    public void testStacInputsServiceCoreConfiguration_CreatesHttpStacDownloaderBean() {
        assertThat(applicationContext.getBean("httpStacDownloader")).isInstanceOf(HttpStacDownloader.class);
    }

    @Test
    public void testStacInputsServiceCoreConfiguration_CreatesStacInputsServiceBean() {
        assertThat(applicationContext.getBean("stacInputsService")).isInstanceOf(StacInputsService.class);
    }

    @Test
    public void testStacInputsServiceCoreConfiguration_CreatesStacItemsS3BucketBean() {
        assertThat(applicationContext.getBean("stacItemsS3Bucket")).isInstanceOf(DefaultStacItemsS3Bucket.class);
    }

    @Test
    public void testStacInputsServiceCoreConfiguration_CreatesStacDownloadersWithOnlyHttpStacDownloader() {
        List<StacDownloader> stacDownloaders =
                (List<StacDownloader>) applicationContext.getBean("stacDownloaders");
        assertThat(stacDownloaders).hasSize(1);
        assertThat(stacDownloaders.get(0)).isInstanceOf(HttpStacDownloader.class);
    }

    @Test
    public void testClientExecuteCall_ThrowsSocketTimeoutException_WhenExternalServerDoesNotRespondAndAfterReachingReadTimeout() throws Exception {
        webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/path"),
                MockWebServerWrapper.response(HttpStatus.OK, SocketPolicy.NO_RESPONSE));
        OkHttpClient stacClient = (OkHttpClient) applicationContext.getBean("stacClient");
        URL stacUrl = URI.create("http://localhost:" + mockServerPort + "/path").toURL();

        Long start = System.currentTimeMillis();
        try {
            stacClient.newCall(new Request.Builder().url(stacUrl).get().build()).execute();
            fail();
        } catch (SocketTimeoutException e) {
            Long finish = System.currentTimeMillis();
            long timeout = (long) 3 * 1000;
            assertThat((finish - start)).isBetween(timeout - 350, timeout + 350);
        }
    }

    @Test
    public void testClientExecuteCall_ThrowsSocketTimeoutException_WhenExternalServerDoesNotRespondAndAfterReachingConnectionTimeout() throws Exception {
        OkHttpClient stacClient = (OkHttpClient) applicationContext.getBean("stacClient");
        URL stacUrl = URI.create("http://10.0.10.10").toURL();

        Long start = System.currentTimeMillis();
        try {
            stacClient.newCall(new Request.Builder().url(stacUrl).get().build()).execute();
            fail();
        } catch (SocketTimeoutException e) {
            Long finish = System.currentTimeMillis();
            long timeout = (long) 2 * 1000;
            assertThat((finish - start)).isBetween(timeout - 350, timeout + 350);
        }
    }
}
