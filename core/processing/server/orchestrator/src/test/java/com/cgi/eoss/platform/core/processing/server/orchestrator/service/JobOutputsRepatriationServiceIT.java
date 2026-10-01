package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor.Parameter;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import okhttp3.mockwebserver.MockWebServer;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class JobOutputsRepatriationServiceIT {

    private static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources");

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private JobOutputsRepatriationService jobOutputsRepatriationService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private JobDataService jobDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Value("${platform.orchestrator.mockserver.port}")
    private Integer webServerPort;

    @Value("${platform.orchestrator.outputProducts.baseDir}")
    private Path outputProductsPath;

    private MockWebServerWrapper webServer;

    private User platformUser;

    private PlatformService platformService;


    @Before
    public void init() throws Exception {

        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(webServerPort);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        platformUser = userDataService.save(ProcessingCoreEntities.createUser().build());
        platformService = saveServiceWithOutput();

        FilesUtils.deleteDirContentsIfExists(outputProductsPath);
    }

    @After
    public void shutdown() throws Exception {

        FilesUtils.deleteDirContentsIfExists(outputProductsPath);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }
    }

    @Test
    public void testRepatriate_RepatriatesTheOutputObjectsAndLogsStartedEvent() throws Exception {
        Job job = saveJob(platformUser);
        Path filePath = BASE_TEST_PATH.resolve("point.zip");

        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/"), response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/?prefix=" + job.getExtId() + "%2F&encoding-type=url"),
                response(HttpStatus.OK, "<ListBucketResult><IsTruncated>false</IsTruncated><Contents><Key>" + job.getExtId()
                        + "/outputIdOne/productOne.zip</Key></Contents><Name>testBucket</Name></ListBucketResult>"));
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/" + job.getExtId() + "/outputIdOne/productOne.zip"),
                response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/" + job.getExtId() + "/outputIdOne/productOne.zip"),
                response(HttpStatus.OK, Files.readAllBytes(filePath)));

        Long beforeRepatriation = Instant.now().getEpochSecond();
        List<RepatriatedOutput> repatriatedOutputs = jobOutputsRepatriationService.repatriate(job);
        Long afterRepatriation = Instant.now().getEpochSecond();

        // Verify the repatriated output
        assertThat(repatriatedOutputs).hasSize(1);
        RepatriatedOutput repatriatedOutput = repatriatedOutputs.get(0);
        assertThat(repatriatedOutput.getOutputId()).isEqualTo("outputIdOne");
        assertThat(repatriatedOutput.getRepatriatedPaths()).hasSize(1);
        assertThat(repatriatedOutput.getRepatriatedPaths().get(0).getFileName().toString()).isEqualTo("productOne.zip");

        // Verify the output object has been written to the output product storage
        Path repatriatedFile = outputProductsPath.resolve(job.getExtId()).resolve("outputIdOne").resolve("productOne.zip");
        assertThat(repatriatedFile).exists();
        assertThat(Files.readAllBytes(repatriatedFile)).isEqualTo(Files.readAllBytes(filePath));

    }

    @Test
    public void testRepatriate_ThrowsAmazonS3ExceptionAndLogsNoEvent_WhenOutputObjectIsNotAvailable() throws Exception {
        Job job = saveJob(platformUser);

        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/"), response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/?prefix=" + job.getExtId() + "%2F&encoding-type=url"),
            response(HttpStatus.OK, "<ListBucketResult><IsTruncated>false</IsTruncated><Contents><Key>" + job.getExtId()
                                    + "/outputIdOne/productOne.zip</Key></Contents><Name>testBucket</Name></ListBucketResult>"));
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/" + job.getExtId() + "/outputIdOne/productOne.zip"),
            response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/" + job.getExtId() + "/outputIdOne/productOne.zip"),
            response(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> jobOutputsRepatriationService.repatriate(job))
            .isInstanceOf(AmazonS3Exception.class)
            .hasMessage("Client Error (Service: Amazon S3; Status Code: 404; Error Code: 404 Client Error; Request ID: null; S3 Extended Request ID: null; Proxy: null)");

        // Verify nothing has been written to the output product storage
        assertThat(outputProductsPath.resolve(job.getExtId())).doesNotExist();

    }

    @Test
    public void testRepatriate_ReturnsNoOutputs_WhenBucketDoesNotExist() throws Exception {
        Job job = saveJob(platformUser);

        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/"), response(HttpStatus.NOT_FOUND));

        List<RepatriatedOutput> repatriatedOutputs = jobOutputsRepatriationService.repatriate(job);

        assertThat(repatriatedOutputs).isEmpty();
        assertThat(outputProductsPath.resolve(job.getExtId())).doesNotExist();
    }

    private PlatformService saveServiceWithOutput() {
        PlatformService service = ProcessingCoreEntities.createPlatformService(platformUser).build();
        service.setServiceDescriptor(new PlatformServiceDescriptor());
        Parameter dataOutput = new Parameter();
        dataOutput.setId("outputIdOne");
        service.getServiceDescriptor().setDataOutputs(Collections.singletonList(dataOutput));
        return serviceDataService.save(service);
    }

    private Job saveJob(User owner) {
        Multimap<String, String> inputs = ArrayListMultimap.create();
        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(owner, platformService));
        jobConfig.setInputs(inputs);
        jobConfig = jobConfigDataService.save(jobConfig);
        return jobDataService.save(ProcessingCoreEntities.createJob(owner, jobConfig)
                .startTime(LocalDateTime.now())
                .build());
    }

    private static Customization regexMatcher(String jsonPath) {
        return new Customization(jsonPath, new RegularExpressionValueMatcher<>());
    }
}
