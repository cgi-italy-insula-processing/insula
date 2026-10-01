package com.cgi.eoss.platform.core.processing.server.orchestrator;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformServiceValidator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.CwlService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class OrchestratorCoreConfigIT {

    @Autowired
    protected ApplicationContext applicationContext;

    @Value("${platform.orchestrator.mockserver.port}")
    protected Integer mockServerPort;

    private MockWebServerWrapper webServer;

    @Before
    public void init() throws Exception {
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
    public void testOrchestratorCoreConfig_CreatesCwlHttpClientBean() {
        OkHttpClient cwlHttpClient = (OkHttpClient) applicationContext.getBean("cwlHttpClient");
        assertThat(cwlHttpClient).isNotNull();
    }

    @Test
    public void testOrchestratorCoreConfig_CreatesCwlServiceBean() {
        CwlService cwlService = applicationContext.getBean(CwlService.class);
        assertThat(cwlService).isNotNull();
    }

    @Test
    public void testClientExecuteCall_ThrowsSocketTimeoutException_WhenExternalServerDoesNotRespondAndAfterReachingReadTimeout() throws Exception {
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, SocketPolicy.NO_RESPONSE));
        }
        OkHttpClient cwlHttpClient = (OkHttpClient) applicationContext.getBean("cwlHttpClient");
        URL cwlUrl = URI.create("http://localhost:" + mockServerPort + "/cwl/app-package.cwl").toURL();

        long start = System.currentTimeMillis();
        try {
            cwlHttpClient.newCall(new Request.Builder().url(cwlUrl).get().header("Accept", "*/*").build()).execute();
            fail();
        } catch (SocketTimeoutException e) {
            long finish = System.currentTimeMillis();
            long timeout = 3 * 1000L;
            assertThat(finish - start).isBetween(timeout - 350, timeout + 350);
        }
    }

    @Test
    public void testClientExecuteCall_ThrowsSocketTimeoutException_WhenExternalServerDoesNotRespondAndAfterReachingConnectionTimeout() throws Exception {
        OkHttpClient cwlHttpClient = (OkHttpClient) applicationContext.getBean("cwlHttpClient");
        URL cwlUrl = URI.create("http://10.0.10.10").toURL();

        long start = System.currentTimeMillis();
        try {
            cwlHttpClient.newCall(new Request.Builder().url(cwlUrl).get().header("Accept", "*/*").build()).execute();
            fail();
        } catch (SocketTimeoutException e) {
            long finish = System.currentTimeMillis();
            long timeout = 2 * 1000L;
            assertThat(finish - start).isBetween(timeout - 350, timeout + 350);
        }
    }

    @Test
    public void testOrchestratorCoreConfig_CreatesPlatformServiceValidatorBean() {
        PlatformServiceValidator platformServiceValidator = applicationContext.getBean(PlatformServiceValidator.class);
        assertThat(platformServiceValidator).isNotNull();
    }
}