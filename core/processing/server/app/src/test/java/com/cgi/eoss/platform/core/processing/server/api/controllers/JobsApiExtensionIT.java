package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.api.ApiConfig;
import com.cgi.eoss.platform.core.processing.server.api.ApiTestConfig;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputs;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobSpecMapper;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobSubmissionRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.StopJob;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ApiConfig.class, ApiTestConfig.class})
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-application.properties")
public class JobsApiExtensionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private JobDataService jobDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private QueueService queueService;

    private User defaultUser;

    @Value("${platform.orchestrator.job.stopper.pendingQueueName}")
    private String pendingJobsQueueName;
    @Value("${platform.orchestrator.job.stopper.waitingQueueName}")
    private String waitingJobsQueueName;

    @Value("${platform.orchestrator.outputProducts.baseDir}")
    private Path outputProductsBaseDir;

    @Before
    public void init() {
        FilesUtils.deleteDirContentsIfExists(outputProductsBaseDir);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        defaultUser = userDataService.save(ProcessingCoreEntities
                .createUser()
                .name("test-user")
                .role(Role.USER)
                .build());

        initQueue(waitingJobsQueueName);
        initQueue(pendingJobsQueueName);
        initQueue(ProcessingCoreQueueNames.JOB_STOP_REQUESTS);
    }

    @After
    public void shutdown() {
        FilesUtils.deleteDirContentsIfExists(outputProductsBaseDir);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(0);
        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(0);
        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(0);

    }

    @Test
    public void testPOSTJobTerminate_EnqueuesMessageToTerminateJobsExecutionAndReturnsNoContent() throws Exception {

        Job job = createBaseJobToPersist();
        job.setStatus(Job.Status.RUNNING);
        job = jobDataService.save(job);

        mockMvc.perform(post("/api/jobs/"+ job.getId() + "/terminate" )
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNoContent());

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(1);

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_STOP_REQUESTS);

        Map<String, Object> headers = message.getHeaders();
        assertThat(headers).hasSize(2);
        assertThat(headers.get("workerId")).isEqualTo("jobWorkerId");
        assertThat(headers.get("jobId")).isEqualTo(job.getId().toString());

        StopJob payload = (StopJob) message.getPayload();

        assertThat(payload.getJob().getId()).isEqualTo(job.getExtId());
        assertThat(payload.getJob().getIntJobId()).isEqualTo(job.getId().toString());
        assertThat(payload.getJob().getUserId()).isEqualTo("test-user");
        assertThat(payload.getJob().getServiceId()).isEqualTo("test-service");

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(0);

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job persistedJob = persistedJobs.get(0);
        assertThat(persistedJob.getId()).isEqualTo(job.getId());
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.RUNNING);
    }

    @Test
    public void testGETJobCancel_CancelsTheJobExecutionAndReturnsOk_WhenJobIsInPendingStatus() throws Exception {

        Job pendingJob = createBaseJobToPersist();
        pendingJob.setStatus(Job.Status.PENDING);
        pendingJob.setParent(false);
        pendingJob = jobDataService.save(pendingJob);

        HashMap<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("jobId", String.valueOf(pendingJob.getId()));
        queueService.sendObject(
                pendingJobsQueueName,
                messageHeaders,
                buildJobSpecForJob(pendingJob),
                1);

        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(1);

        assertThat(mockMvc.perform(get("/api/jobs/"+ pendingJob.getId() + "/cancel" )
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");

        assertThat(queueService.getQueueLength(pendingJobsQueueName)).isEqualTo(0);

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job canceledJob = persistedJobs.get(0);
        assertThat(canceledJob.getId()).isEqualTo(pendingJob.getId());
        assertThat(canceledJob.getConfig()).isEqualTo(pendingJob.getConfig());
        assertThat(canceledJob.getOwner()).isEqualTo(defaultUser);
        assertThat(canceledJob.isParent()).isFalse();
        assertThat(canceledJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testGETJobCancel_CancelsTheJobExecutionAndReturnsOk_WhenJobIsInWaitingStatus() throws Exception {

        Job waitingJob = createBaseJobToPersist();
        waitingJob.setStatus(Job.Status.WAITING);
        waitingJob.setParent(false);
        waitingJob = jobDataService.save(waitingJob);

        HashMap<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("jobId", String.valueOf(waitingJob.getId()));
        queueService.sendObject(
                waitingJobsQueueName,
                messageHeaders,
                buildJobSpecForJob(waitingJob),
                1);
        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(1);

        assertThat(mockMvc.perform(get("/api/jobs/"+ waitingJob.getId() + "/cancel" )
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");

        assertThat(queueService.getQueueLength(waitingJobsQueueName)).isEqualTo(0);

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job canceledJob = persistedJobs.get(0);
        assertThat(canceledJob.getId()).isEqualTo(waitingJob.getId());
        assertThat(canceledJob.getConfig()).isEqualTo(waitingJob.getConfig());
        assertThat(canceledJob.getOwner()).isEqualTo(defaultUser);
        assertThat(canceledJob.isParent()).isFalse();
        assertThat(canceledJob.getStatus()).isEqualTo(Job.Status.CANCELLED);
    }

    @Test
    public void testGETJobCancel_DoesNotCancelTheJobExecutionAndReturnsOk_WhenJobIsAlreadyStarted() throws Exception {

        Job runningJob = createBaseJobToPersist();
        runningJob.setStatus(Job.Status.RUNNING);
        runningJob.setParent(false);
        runningJob = jobDataService.save(runningJob);

        assertThat(mockMvc.perform(get("/api/jobs/"+ runningJob.getId() + "/cancel" )
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");

        List<Job> persistedJobs = jobDataService.getAll();
        assertThat(persistedJobs).hasSize(1);
        Job unchangedRunningJob = persistedJobs.get(0);
        assertThat(unchangedRunningJob.getId()).isEqualTo(runningJob.getId());
        assertThat(unchangedRunningJob.getConfig()).isEqualTo(runningJob.getConfig());
        assertThat(unchangedRunningJob.getOwner()).isEqualTo(defaultUser);
        assertThat(unchangedRunningJob.isParent()).isFalse();
        assertThat(unchangedRunningJob.getStatus()).isEqualTo(Job.Status.RUNNING);
    }

    @Test
    public void testGETJobOutput_ReturnsStacCollectionWithAssetsLinkingToTheDownloadableFiles_WhenOutputIsStac() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("stacOutputId", ImmutableMap.of("type", "STAC")));
        Files.copy(Paths.get("src", "test", "resources", "stac", "stac-item.json"),
                createOutputFolder(job, "stacOutputId").resolve("stac-item.json"));
        createOutputFile(job, "stacOutputId", "fileName", "data content");

        mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/stacOutputId")
                        .accept("application/geo+json")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/geo+json"))
                .andExpect(content().json("{"
                        + "  \"type\": \"FeatureCollection\","
                        + "  \"features\": [{"
                        + "    \"stac_version\": \"1.1.0\","
                        + "    \"id\": \"stacItemId\","
                        + "    \"type\": \"Feature\","
                        + "    \"geometry\": null,"
                        + "    \"properties\": {\"datetime\": \"2020-01-15T10:00:00Z\"},"
                        + "    \"assets\": {\"enclosure\": {"
                        + "      \"href\": \"http://insula-test:8443/api/jobs/" + job.getId() + "/outputs/stacOutputId?filename=fileName\","
                        + "      \"type\": null,"
                        + "      \"roles\": [\"data\"]"
                        + "    }}"
                        + "  }]"
                        + "}", true));
    }

    @Test
    public void testGETJobOutput_ReturnsStacCollectionWithOneDefaultItemPerFileNestedFoldersIncluded_WhenOutputIsNotStac() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "nested/result.tif", "result content", Instant.parse("2026-09-24T10:15:30Z"));
        createOutputFile(job, "outputId", "report.json", "{}", Instant.parse("2026-09-25T11:16:31Z"));

        mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/geo+json"))
                .andExpect(content().json("{"
                        + "  \"type\": \"FeatureCollection\","
                        + "  \"features\": [{"
                        + "    \"stac_version\": \"1.1.0\","
                        + "    \"id\": \"jobExtId_outputId_nested/result.tif\","
                        + "    \"type\": \"Feature\","
                        + "    \"geometry\": null,"
                        + "    \"properties\": {\"datetime\": \"2026-09-24T10:15:30Z\"},"
                        + "    \"assets\": {\"enclosure\": {"
                        + "      \"href\": \"http://insula-test:8443/api/jobs/" + job.getId() + "/outputs/outputId?filename=nested%2Fresult.tif\","
                        + "      \"type\": null,"
                        + "      \"roles\": [\"data\"]"
                        + "    }}"
                        + "  }, {"
                        + "    \"stac_version\": \"1.1.0\","
                        + "    \"id\": \"jobExtId_outputId_report.json\","
                        + "    \"type\": \"Feature\","
                        + "    \"geometry\": null,"
                        + "    \"properties\": {\"datetime\": \"2026-09-25T11:16:31Z\"},"
                        + "    \"assets\": {\"enclosure\": {"
                        + "      \"href\": \"http://insula-test:8443/api/jobs/" + job.getId() + "/outputs/outputId?filename=report.json\","
                        + "      \"type\": null,"
                        + "      \"roles\": [\"data\"]"
                        + "    }}"
                        + "  }]"
                        + "}", true));
    }

    @Test
    public void testGETJobOutput_ReturnsStacCollectionAsGeoJson_WhenAnyMediaTypeIsAccepted() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "result.tif", "result content", Instant.parse("2026-09-24T10:15:30Z"));

        mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .accept(MediaType.ALL)
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/geo+json"))
                .andExpect(content().json("{"
                        + "  \"type\": \"FeatureCollection\","
                        + "  \"features\": [{"
                        + "    \"stac_version\": \"1.1.0\","
                        + "    \"id\": \"jobExtId_outputId_result.tif\","
                        + "    \"type\": \"Feature\","
                        + "    \"geometry\": null,"
                        + "    \"properties\": {\"datetime\": \"2026-09-24T10:15:30Z\"},"
                        + "    \"assets\": {\"enclosure\": {"
                        + "      \"href\": \"http://insula-test:8443/api/jobs/" + job.getId() + "/outputs/outputId?filename=result.tif\","
                        + "      \"type\": null,"
                        + "      \"roles\": [\"data\"]"
                        + "    }}"
                        + "  }]"
                        + "}", true));
    }

    @Test
    public void testGETJobOutput_ReturnsNotFound_WhenJobDoesNotExist() throws Exception {

        assertThat(mockMvc.perform(get("/api/jobs/424242/outputs/outputId")
                        .accept("application/geo+json")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");
    }

    @Test
    public void testGETJobOutput_ReturnsBadRequest_WhenJobHasNoSuchOutput() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));

        assertThat(mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .accept("application/geo+json")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");
    }

    @Test
    public void testGETJobOutputFile_ReturnsTheFileContentAsAttachmentNestedFoldersIncluded() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "nested/result.tif", "nested result content");

        assertThat(mockMvc.perform(get(URI.create("/api/jobs/" + job.getId() + "/outputs/outputId?filename=nested%2Fresult.tif"))
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''result.tif"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 21))
                .andExpect(content().contentType("image/tiff"))
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("nested result content");
    }

    @Test
    public void testGETJobOutputFile_ReturnsTheFileContentAsAttachment_WhenClientAcceptsGeoJson() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "result.tif", "result content");

        assertThat(mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .param("filename", "result.tif")
                        .accept("application/geo+json")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''result.tif"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 14))
                .andExpect(content().contentType("image/tiff"))
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("result content");
    }

    @Test
    public void testGETJobOutputFile_ReturnsTheFileContentAsOctetStreamAttachment_WhenFileExtensionIsUnknown() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "result.unknown", "result content");

        assertThat(mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .param("filename", "result.unknown")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''result.unknown"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 14))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("result content");
    }

    @Test
    public void testGETJobOutputFile_ReturnsNotFound_WhenJobDoesNotExist() throws Exception {

        assertThat(mockMvc.perform(get("/api/jobs/424242/outputs/outputId")
                        .param("filename", "result.tif")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");
    }

    @Test
    public void testGETJobOutputFile_ReturnsBadRequest_WhenFileDoesNotExist() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "result.tif", "result content");

        assertThat(mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .param("filename", "missing.tif")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");
    }

    @Test
    public void testGETJobOutputFile_ReturnsBadRequest_WhenFilenameEscapesTheOutputFolder() throws Exception {

        Job job = persistJobWithOutputs("jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        createOutputFile(job, "outputId", "result.tif", "result content");

        assertThat(mockMvc.perform(get("/api/jobs/" + job.getId() + "/outputs/outputId")
                        .param("filename", "../outputId/result.tif")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString())
                .isEqualTo("");
    }

    private void initQueue(String queueName) {
        queueService.sendObject(queueName, "{}");
        queueService.receiveObjectNoWait(queueName);
        assertThat(queueService.getQueueLength(queueName)).isEqualTo(0);
    }

    private Job createBaseJobToPersist() {
        Job job = ProcessingCoreEntities.createJob(
                defaultUser,
                jobConfigDataService.save(
                        new JobConfig(
                                defaultUser,
                                serviceDataService.save(
                                        ProcessingCoreEntities.createPlatformService(defaultUser)
                                                .name("test-service")
                                                .build()))
                )).build();
        job.setWorkerId("jobWorkerId");
        return job;
    }

    private Job persistJobWithOutputs(String jobExtId, PlatformServiceDescriptor.Parameter... outputs) {
        PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
        serviceDescriptor.setDataOutputs(ImmutableList.copyOf(outputs));

        PlatformService service = serviceDataService.save(ProcessingCoreEntities.createPlatformService(defaultUser)
                .name("test-service")
                .platformServiceDescriptor(serviceDescriptor)
                .build());
        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(defaultUser, service));
        return jobDataService.save(ProcessingCoreEntities.createJob(defaultUser, jobConfig)
                .extId(jobExtId)
                .build());
    }

    private Path createOutputFolder(Job job, String outputId) throws Exception {
        return Files.createDirectories(outputProductsBaseDir.resolve(job.getExtId()).resolve(outputId));
    }

    private Path createOutputFile(Job job, String outputId, String filename, String content) throws Exception {
        Path outputFile = createOutputFolder(job, outputId).resolve(filename);
        Files.createDirectories(outputFile.getParent());
        return Files.write(outputFile, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path createOutputFile(Job job, String outputId, String filename, String content, Instant lastModified) throws Exception {
        Path outputFile = createOutputFile(job, outputId, filename, content);
        Files.setLastModifiedTime(outputFile, FileTime.from(lastModified));
        return outputFile;
    }

    private static PlatformServiceDescriptor.Parameter createOutput(String outputId, Map<String, String> platformMetadata) {
        return PlatformServiceDescriptor.Parameter.builder()
                .id(outputId)
                .platformMetadata(platformMetadata)
                .build();
    }

    private static JobSpec buildJobSpecForJob(Job job) {

        JobSpec.Builder jobSpecBuilder = JobSpecMapper.buildJobSpecBuilder(
                JobSubmissionRequest.builder()
                        .job(job)
                        .jobInputs(JobInputs.builder().inputs(new HashMap<>()).build())
                        .jobResourceRequirement(new JobResourceRequirement())
                        .build(),
                Collections.emptyList());
        jobSpecBuilder.setKind(Kind.WORKFLOW);
        return jobSpecBuilder.build();
    }

}