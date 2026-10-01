package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
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
import com.google.protobuf.Timestamp;
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
import java.time.LocalDateTime;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class CoreWorkerJobUpdatesManagerIT {

    @Autowired
    private CoreWorkerJobUpdatesManager coreWorkerJobUpdatesManager;

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
    public void testOnJobProcessingCompleted_CompletesTheJobAndPersistsItsOutputs() throws Exception {

        Job job = persistJob("jobExtId", persistServiceWithOutput("outputIdOne"), JobStep.PROCESSING, Job.Status.RUNNING);
        expectOutputObjectInStorage("jobExtId", "outputIdOne/productOne.zip", Paths.get("src", "test", "resources", "point.zip"));

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        Job persistedJob = jobDataService.getById(job.getId()).get();
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.COMPLETED);
        assertThat(persistedJob.getPhase()).isEqualTo(JobStep.OUTPUT_LIST);
        assertThat(persistedJob.getStage()).isEqualTo("Step 3 of 3: Output-List");
        assertThat(persistedJob.getEndTime()).isBetween(beforeCall, afterCall);
        assertThat(persistedJob.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "jobExtId/outputIdOne/productOne.zip"));
        assertThat(outputProductsBaseDir.resolve("jobExtId/outputIdOne/productOne.zip"))
                .hasBinaryContent(Files.readAllBytes(Paths.get("src", "test", "resources", "point.zip")));
    }

    @Test
    public void testOnJobProcessingCompleted_CompletesTheJobAndPersistsOnePathPerExtractedFile_WhenOutputTypeIsStac() throws Exception {

        Job job = persistJob("jobExtId", persistServiceWithStacOutput("outputIdOne"), JobStep.PROCESSING, Job.Status.RUNNING);
        expectOutputObjectInStorage("jobExtId", "outputIdOne/test-item.zip", Paths.get("src", "test", "resources", "test-item.zip"));

        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());

        Job persistedJob = jobDataService.getById(job.getId()).get();
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.COMPLETED);
        assertThat(persistedJob.getPhase()).isEqualTo(JobStep.OUTPUT_LIST);
        Multimap<String, String> persistedOutputs = persistedJob.getOutputs();
        assertThat(persistedOutputs.keySet()).containsExactly("outputIdOne");
        assertThat(persistedOutputs.get("outputIdOne"))
                .containsExactlyInAnyOrder("jobExtId/outputIdOne/content", "jobExtId/outputIdOne/test-item.json");
        assertThat(FilesUtils.list(outputProductsBaseDir.resolve("jobExtId/outputIdOne"))).hasSize(2);
        assertThat(outputProductsBaseDir.resolve("jobExtId/outputIdOne/content")).hasContent("justsomebytes");
        assertThat(outputProductsBaseDir.resolve("jobExtId/outputIdOne/test-item.json")).exists();
    }

    @Test
    public void testOnJobProcessingCompleted_CompletesTheParentJobAndAggregatesTheOutputs_WhenAllSubJobsAreCompleted() throws Exception {

        PlatformService service = persistServiceWithOutput("outputIdOne");
        Job parentJob = persistParentJob("parentJobExtId", service);
        persistSubJob("subJobAExtId", service, parentJob, JobStep.OUTPUT_LIST, Job.Status.COMPLETED);
        Job subJobB = persistSubJob("subJobBExtId", service, parentJob, JobStep.PROCESSING, Job.Status.RUNNING);
        expectOutputObjectInStorage("subJobBExtId", "outputIdOne/productOne.zip", Paths.get("src", "test", "resources", "point.zip"));

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(subJobB, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        Job persistedSubJobB = jobDataService.getById(subJobB.getId()).get();
        assertThat(persistedSubJobB.getStatus()).isEqualTo(Job.Status.COMPLETED);
        assertThat(persistedSubJobB.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "subJobBExtId/outputIdOne/productOne.zip"));

        Job persistedParentJob = jobDataService.getById(parentJob.getId()).get();
        assertThat(persistedParentJob.getStatus()).isEqualTo(Job.Status.COMPLETED);
        assertThat(persistedParentJob.getPhase()).isEqualTo(JobStep.OUTPUT_LIST);
        assertThat(persistedParentJob.getStage()).isEqualTo("Step 3 of 3: Output-List");
        assertThat(persistedParentJob.getEndTime()).isBetween(beforeCall, afterCall);
        assertThat(persistedParentJob.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "subJobBExtId/outputIdOne/productOne.zip"));
    }

    @Test
    public void testOnJobProcessingCompleted_AggregatesTheOutputsIntoTheStillRunningParentJob_WhenOtherSubJobsAreNotCompleted() throws Exception {

        PlatformService service = persistServiceWithOutput("outputIdOne");
        Job parentJob = persistParentJob("parentJobExtId", service);
        persistSubJob("subJobAExtId", service, parentJob, JobStep.PROCESSING, Job.Status.RUNNING);
        Job subJobB = persistSubJob("subJobBExtId", service, parentJob, JobStep.PROCESSING, Job.Status.RUNNING);
        expectOutputObjectInStorage("subJobBExtId", "outputIdOne/productOne.zip", Paths.get("src", "test", "resources", "point.zip"));

        coreWorkerJobUpdatesManager.onJobProcessingCompleted(subJobB, Timestamp.getDefaultInstance());

        Job persistedSubJobB = jobDataService.getById(subJobB.getId()).get();
        assertThat(persistedSubJobB.getStatus()).isEqualTo(Job.Status.COMPLETED);

        Job persistedParentJob = jobDataService.getById(parentJob.getId()).get();
        assertThat(persistedParentJob.getStatus()).isEqualTo(Job.Status.RUNNING);
        assertThat(persistedParentJob.getPhase()).isEqualTo(JobStep.PROCESSING);
        assertThat(persistedParentJob.getEndTime()).isNull();
        assertThat(persistedParentJob.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "subJobBExtId/outputIdOne/productOne.zip"));
    }

    @Test
    public void testOnJobProcessingCompleted_EndsTheJobWithErrorAndPersistsNoOutputs_WhenOutputRepatriationFails() throws Exception {

        Job job = persistJob("jobExtId", persistServiceWithOutput("outputIdOne"), JobStep.PROCESSING, Job.Status.RUNNING);
        expectOutputObjectNotAvailableInStorage("jobExtId", "outputIdOne/productOne.zip");

        LocalDateTime beforeCall = LocalDateTime.now();
        coreWorkerJobUpdatesManager.onJobProcessingCompleted(job, Timestamp.getDefaultInstance());
        LocalDateTime afterCall = LocalDateTime.now();

        Job persistedJob = jobDataService.getById(job.getId()).get();
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(persistedJob.getPhase()).isEqualTo(JobStep.OUTPUT_LIST);
        assertThat(persistedJob.getEndTime()).isBetween(beforeCall, afterCall);
        assertThat(persistedJob.getOutputs()).isNull();
        assertThat(outputProductsBaseDir.resolve("jobExtId")).doesNotExist();
    }

    @Test
    public void testOnJobProcessingCompleted_EndsTheJobAndItsParentJobWithError_WhenOutputRepatriationFailsAndJobIsSubJob() throws Exception {

        PlatformService service = persistServiceWithOutput("outputIdOne");
        Job parentJob = persistParentJob("parentJobExtId", service);
        Job subJob = persistSubJob("subJobExtId", service, parentJob, JobStep.PROCESSING, Job.Status.RUNNING);
        expectOutputObjectNotAvailableInStorage("subJobExtId", "outputIdOne/productOne.zip");

        coreWorkerJobUpdatesManager.onJobProcessingCompleted(subJob, Timestamp.getDefaultInstance());

        Job persistedSubJob = jobDataService.getById(subJob.getId()).get();
        assertThat(persistedSubJob.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(persistedSubJob.getOutputs()).isNull();

        Job persistedParentJob = jobDataService.getById(parentJob.getId()).get();
        assertThat(persistedParentJob.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(persistedParentJob.getEndTime()).isNull();
        assertThat(persistedParentJob.getOutputs()).isNull();
    }

    private void expectOutputObjectInStorage(String jobExtId, String objectPath, Path content) throws Exception {
        expectOutputObjectListedInStorage(jobExtId, objectPath);
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/" + jobExtId + "/" + objectPath),
                response(HttpStatus.OK, Files.readAllBytes(content)));
    }

    private void expectOutputObjectNotAvailableInStorage(String jobExtId, String objectPath) {
        expectOutputObjectListedInStorage(jobExtId, objectPath);
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/" + jobExtId + "/" + objectPath),
                response(HttpStatus.NOT_FOUND));
    }

    private void expectOutputObjectListedInStorage(String jobExtId, String objectPath) {
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/"), response(HttpStatus.OK));
        webServer.expect(request(HttpMethod.GET, "/s3/jobOutputs-bucket/?prefix=" + jobExtId + "%2F&encoding-type=url"),
                response(HttpStatus.OK, "<ListBucketResult><IsTruncated>false</IsTruncated><Contents><Key>" + jobExtId + "/"
                        + objectPath + "</Key></Contents><Name>jobOutputs-bucket</Name></ListBucketResult>"));
        webServer.expect(request(HttpMethod.HEAD, "/s3/jobOutputs-bucket/" + jobExtId + "/" + objectPath), response(HttpStatus.OK));
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

    private Job persistJob(String jobExtId, PlatformService service, JobStep phase, Job.Status status) {
        return jobDataService.save(createJob(jobExtId, service, null, phase, status));
    }

    private Job persistParentJob(String jobExtId, PlatformService service) {
        Job parentJob = createJob(jobExtId, service, null, JobStep.PROCESSING, Job.Status.RUNNING);
        parentJob.setParent(true);
        return jobDataService.save(parentJob);
    }

    private Job persistSubJob(String jobExtId, PlatformService service, Job parentJob, JobStep phase, Job.Status status) {
        return jobDataService.save(createJob(jobExtId, service, parentJob, phase, status));
    }

    private Job createJob(String jobExtId, PlatformService service, Job parentJob, JobStep phase, Job.Status status) {
        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(owner, service));
        Job job = ProcessingCoreEntities.createJob(owner, jobConfig)
                .extId(jobExtId)
                .parentJob(parentJob)
                .phase(phase)
                .status(status)
                .build();
        job.setStage(phase.getText());
        return job;
    }

}
