package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class CoreOutputProcessorIT {

    @Autowired
    private CoreOutputProcessor coreOutputProcessor;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Autowired
    private JobDataService jobDataService;

    @Value("${platform.orchestrator.mockserver.port}")
    private Integer webServerPort;

    @Value("${platform.orchestrator.outputProducts.baseDir}")
    private Path outputProductsBaseDir;

    private MockWebServerWrapper webServer;

    private User owner;

    @Before
    public void init() throws Exception {
        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(webServerPort);

        FilesUtils.deleteDirContentsIfExists(outputProductsBaseDir);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        owner = userDataService.save(ProcessingCoreEntities.createUser().build());
    }

    @After
    public void shutdown() throws Exception {
        FilesUtils.deleteDirContentsIfExists(outputProductsBaseDir);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }
    }

    @Test
    public void testProcessOutputs_PersistsTheRepatriatedOutputPathsRelativeToTheOutputProductsBaseDir() throws Exception {

        Job job = persistJob("jobExtId", persistServiceWithOutput("outputIdOne"));
        expectOutputObjectInStorage("jobExtId", "outputIdOne/productOne.zip", Paths.get("src", "test", "resources", "point.zip"));

        coreOutputProcessor.processOutputs(job);

        Job persistedJob = jobDataService.getById(job.getId()).get();
        assertThat(persistedJob.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "jobExtId/outputIdOne/productOne.zip"));
        assertThat(outputProductsBaseDir.resolve("jobExtId/outputIdOne/productOne.zip"))
                .hasBinaryContent(Files.readAllBytes(Paths.get("src", "test", "resources", "point.zip")));
    }

    @Test
    public void testProcessOutputs_PersistsOnePathPerFileExtractedFromTheOutputArchive_WhenOutputTypeIsStac() throws Exception {

        Job job = persistJob("jobExtId", persistServiceWithStacOutput("outputIdOne"));
        expectOutputObjectInStorage("jobExtId", "outputIdOne/test-item.zip", Paths.get("src", "test", "resources", "test-item.zip"));

        coreOutputProcessor.processOutputs(job);

        Multimap<String, String> persistedOutputs = jobDataService.getById(job.getId()).get().getOutputs();
        assertThat(persistedOutputs.keySet()).containsExactly("outputIdOne");
        assertThat(persistedOutputs.get("outputIdOne"))
                .containsExactlyInAnyOrder("jobExtId/outputIdOne/content", "jobExtId/outputIdOne/test-item.json");
        assertThat(FilesUtils.list(outputProductsBaseDir.resolve("jobExtId/outputIdOne"))).hasSize(2);
        assertThat(outputProductsBaseDir.resolve("jobExtId/outputIdOne/content")).hasContent("justsomebytes");
        assertThat(outputProductsBaseDir.resolve("jobExtId/outputIdOne/test-item.json")).exists();
    }

    @Test
    public void testProcessOutputs_AggregatesTheOutputPathsIntoTheParentJob_WhenJobIsSubJob() throws Exception {

        PlatformService service = persistServiceWithOutput("outputIdOne");
        Job parentJob = persistParentJob("parentJobExtId", service);
        Job subJob = persistSubJob("subJobExtId", service, parentJob);
        expectOutputObjectInStorage("subJobExtId", "outputIdOne/productOne.zip", Paths.get("src", "test", "resources", "point.zip"));

        coreOutputProcessor.processOutputs(subJob);

        assertThat(jobDataService.getById(subJob.getId()).get().getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "subJobExtId/outputIdOne/productOne.zip"));
        assertThat(jobDataService.getById(parentJob.getId()).get().getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "subJobExtId/outputIdOne/productOne.zip"));
    }

    @Test
    public void testProcessOutputs_PersistsEmptyOutputs_WhenTheJobOutputsBucketDoesNotExist() throws Exception {

        Job job = persistJob("jobExtId", persistServiceWithOutput("outputIdOne"));
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/"), response(HttpStatus.NOT_FOUND));

        coreOutputProcessor.processOutputs(job);

        assertThat(jobDataService.getById(job.getId()).get().getOutputs()).isEqualTo(ImmutableListMultimap.of());
        assertThat(outputProductsBaseDir.resolve("jobExtId")).doesNotExist();
    }

    private void expectOutputObjectInStorage(String jobExtId, String objectPath, Path content) throws Exception {
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/"), response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/?prefix=" + jobExtId + "%2F&encoding-type=url"),
                response(HttpStatus.OK, "<ListBucketResult><IsTruncated>false</IsTruncated><Contents><Key>" + jobExtId + "/"
                        + objectPath + "</Key></Contents><Name>jobOutputs-bucket</Name></ListBucketResult>"));
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/" + jobExtId + "/" + objectPath), response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/" + jobExtId + "/" + objectPath),
                response(HttpStatus.OK, Files.readAllBytes(content)));
    }

    private PlatformService persistServiceWithOutput(String outputId) {
        return persistService(PlatformServiceDescriptor.Parameter.builder().id(outputId).build());
    }

    private PlatformService persistServiceWithStacOutput(String outputId) {
        return persistService(PlatformServiceDescriptor.Parameter.builder()
                .id(outputId)
                .platformMetadata(ImmutableMap.of("type", "STAC"))
                .build());
    }

    private PlatformService persistService(PlatformServiceDescriptor.Parameter output) {
        PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
        serviceDescriptor.setDataOutputs(ImmutableList.of(output));
        return serviceDataService.save(ProcessingCoreEntities.createPlatformService(owner)
                .platformServiceDescriptor(serviceDescriptor)
                .build());
    }

    private Job persistJob(String jobExtId, PlatformService service) {
        return jobDataService.save(createJob(jobExtId, service, null));
    }

    private Job persistParentJob(String jobExtId, PlatformService service) {
        Job parentJob = createJob(jobExtId, service, null);
        parentJob.setParent(true);
        return jobDataService.save(parentJob);
    }

    private Job persistSubJob(String jobExtId, PlatformService service, Job parentJob) {
        return jobDataService.save(createJob(jobExtId, service, parentJob));
    }

    private Job createJob(String jobExtId, PlatformService service, Job parentJob) {
        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(owner, service));
        return ProcessingCoreEntities.createJob(owner, jobConfig)
                .extId(jobExtId)
                .parentJob(parentJob)
                .build();
    }

}
