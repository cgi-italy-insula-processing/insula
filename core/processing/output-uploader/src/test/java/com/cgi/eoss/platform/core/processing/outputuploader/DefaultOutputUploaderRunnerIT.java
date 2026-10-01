package com.cgi.eoss.platform.core.processing.outputuploader;


import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.requestCustomBodyTest;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = {OutputUploaderCoreTestConfig.class})
@TestPropertySource( locations = "classpath:test-output-uploader-core.properties",
        properties = {
                "job_id=jobId",
                "base_path=outputId"
        })
public class DefaultOutputUploaderRunnerIT {

    @Autowired
    private DefaultOutputUploaderRunner defaultOutputUploaderRunner;

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
    public void testRun_RunsApplication() throws Exception {
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

        defaultOutputUploaderRunner.run();
    }
}