package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.api.ApiConfig;
import com.cgi.eoss.platform.core.processing.server.api.ApiTestConfig;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.jayway.jsonpath.JsonPath;
import okhttp3.mockwebserver.MockWebServer;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static com.cgi.eoss.platform.core.processing.server.api.controllers.ServicesApiIT.readAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(classes = {ApiConfig.class, ApiTestConfig.class})
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-application.properties")
public class JobsApiIT {

    private static final Path CWL_BASE_TEST_PATH = Paths.get("src", "test", "resources", "cwl");

    private static final String CWL_PATH = "/cwl/app-package.cwl";

    private static final String FANOUT_CWL_PATH = "/cwl/app-package-fanout.cwl";

    private static final Service S2_CROPPER_SERVICE = Service.newBuilder()
            .setName("s2-cropper")
            .setDockerImageTag("ogc-crop:0.1")
            .setDescriptorType("CWL")
            .build();

    private static final ResourceRequest S2_CROPPER_RESOURCE_REQUEST = ResourceRequest.newBuilder()
            .setCpus("2.0")
            .setRam("2Mi")
            .setLimits(ResourceSpec.newBuilder().setCpu("2.0").setRam("2Mi").build())
            .build();

    private static final JobParam CROPPED_TIF_OUTPUT = JobParam.newBuilder()
            .setParamName("cropped_tif")
            .setType("STAC")
            .setOutputBinding(OutputBinding.newBuilder().setGlob(".").build())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private JobDataService jobDataService;

    @Value("${platform.cwl.mockserver.port}")
    private Integer mockServerPort;

    @Autowired
    private QueueService queueService;

    private User defaultUser;

    private MockWebServerWrapper webServer;

    private String cwlUrl;

    @Before
    public void init() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        defaultUser = new User("platform-admin");
        defaultUser.setRole(Role.USER);
        defaultUser.setEmail("default-user@test.dev");
        defaultUser.setName("default-user");

        userDataService.save(defaultUser);

        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(mockServerPort);
        cwlUrl = webServer.unwrap().url(CWL_PATH).toString();

        initQueue();
    }

    @After
    public void shutdown() {

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isZero();
    }

    @Test
    public void testGETJob_RetrieveJobWithMatchingId_WhenDetailedJobProjectionIsRequested() throws Exception {

        OffsetDateTime startTest = OffsetDateTime.now(ZoneOffset.UTC);

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcPostJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcPostJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcPostJobConfigResponse.getResponse().getHeader("Location");
        MvcResult mvcJobResponse = mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk()).andReturn();

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(1);

        Job job = allJobs.get(0);
        assertThat(job.getOwner()).isEqualTo(defaultUser);
        assertThat(job.getConfig()).isEqualTo(processingCoreTestDataService.findAllJobConfigs().get(0));
        assertThat(job.getCreated()).isBetween(startTest.minusMinutes(1), startTest.plusMinutes(1));
        assertThat(job.getLastUpdated()).isBetween(startTest.minusMinutes(1), startTest.plusMinutes(1));

        assertThat(job.getExtId()).isNotNull();
        assertThat(job.isParent()).isFalse();

        String contentAsString = mvcJobResponse.getResponse().getContentAsString();
        String jobId = JsonPath.read(contentAsString, "$.id").toString();
        assertThat(jobId).isEqualTo(job.getId().toString());

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(1);

        {
            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }

        job.setOutputs(ImmutableListMultimap.of(
                "outputIdOne", job.getExtId() + "/outputIdOne/productOne.zip",
                "outputIdOne", job.getExtId() + "/outputIdOne/nested/productTwo.tif",
                "outputIdTwo", job.getExtId() + "/outputIdTwo/report.json"));
        job = jobDataService.save(job);

        String detailedJobUrl = "/api/jobs/" + jobId + "?projection=detailedJob";
        mockMvc.perform(get(detailedJobUrl).header("REMOTE_USER", defaultUser.getName())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value("s2-cropper"))
            .andExpect(jsonPath("$.serviceId").value(job.getConfig().getService().getId().toString()))
            .andExpect(jsonPath("$.id").value(job.getId().toString()))
            .andExpect(jsonPath("$.phase").value("CREATED"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.stage").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.extId").value(job.getExtId()))
            .andExpect(jsonPath("$.parent").value(false))
            .andExpect(jsonPath("$.outputFiles.length()").value(3))
            .andExpect(jsonPath("$.outputFiles[0].filename").value("productOne.zip"))
            .andExpect(jsonPath("$.outputFiles[0]._links.download.href")
                    .value("http://localhost/api/jobs/" + job.getId() + "/outputs/outputIdOne?filename=productOne.zip"))
            .andExpect(jsonPath("$.outputFiles[1].filename").value("nested/productTwo.tif"))
            .andExpect(jsonPath("$.outputFiles[1]._links.download.href")
                    .value("http://localhost/api/jobs/" + job.getId() + "/outputs/outputIdOne?filename=nested%2FproductTwo.tif"))
            .andExpect(jsonPath("$.outputFiles[2].filename").value("report.json"))
            .andExpect(jsonPath("$.outputFiles[2]._links.download.href")
                    .value("http://localhost/api/jobs/" + job.getId() + "/outputs/outputIdTwo?filename=report.json"))
            .andReturn();
    }

    @Test
    public void testGETJob_RetrieveParentJobAndSubJobsWithMatchingId_WhenDetailedJobProjectionIsRequestedAndServiceIsParallelProcessor() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, FANOUT_CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package-fanout.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + webServer.unwrap().url(FANOUT_CWL_PATH) + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcPostJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\","
                                + " \"inputs\": {\"band\": [\"B02\", \"B03\"]}}"))
                .andExpect(status().isCreated()).andReturn();
        assertThat(mvcPostJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobResponse = mockMvc.perform(post(mvcPostJobConfigResponse.getResponse().getHeader("Location") + "/launch")
                        .header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk()).andReturn();

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(3);
        List<Job> parentJobs = allJobs.stream().filter(Job::isParent).collect(Collectors.toList());
        assertThat(parentJobs).hasSize(1);
        Job parentJob = parentJobs.get(0);
        List<Job> subJobs = allJobs.stream().filter(job -> !job.isParent()).collect(Collectors.toList());
        assertThat(subJobs).hasSize(2);
        Job subJobA = subJobs.get(0);
        assertThat(subJobA.getParentJob().getId()).isEqualTo(parentJob.getId());
        Job subJobB = subJobs.get(1);
        assertThat(subJobB.getParentJob().getId()).isEqualTo(parentJob.getId());

        String bandA = Iterables.getOnlyElement(subJobA.getConfig().getInputs().get("band"));
        String bandB = Iterables.getOnlyElement(subJobB.getConfig().getInputs().get("band"));
        assertThat(ImmutableList.of(bandA, bandB)).containsExactlyInAnyOrder("B02", "B03");

        String contentAsString = mvcJobResponse.getResponse().getContentAsString();
        assertThat(JsonPath.read(contentAsString, "$.id").toString()).isEqualTo(parentJob.getId().toString());

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);
        Message firstMessage = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
        Message secondMessage = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);

        assertThat(ImmutableList.of(firstMessage.getHeaders(), secondMessage.getHeaders())).containsExactlyInAnyOrder(
                ImmutableMap.<String, Object>of("jobId", String.valueOf(subJobA.getId())),
                ImmutableMap.<String, Object>of("jobId", String.valueOf(subJobB.getId())));

        assertThat(ImmutableList.of(firstMessage.getPayload(), secondMessage.getPayload())).containsExactlyInAnyOrder(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(subJobA.getExtId())
                        .setIntJobId(String.valueOf(subJobA.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(subJobA.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addInputs(JobParam.newBuilder().setParamName("band").addParamValue(bandA).setType("OTHER").build())
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build(),
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(subJobB.getExtId())
                        .setIntJobId(String.valueOf(subJobB.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(subJobB.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addInputs(JobParam.newBuilder().setParamName("band").addParamValue(bandB).setType("OTHER").build())
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build());

        subJobA.setOutputs(ImmutableListMultimap.of("cropped_tif", subJobA.getExtId() + "/cropped_tif/a.tif"));
        subJobA = jobDataService.save(subJobA);
        jobDataService.updateParentJobOutputs(subJobA);
        subJobB.setOutputs(ImmutableListMultimap.of("cropped_tif", subJobB.getExtId() + "/cropped_tif/b.tif"));
        subJobB = jobDataService.save(subJobB);
        jobDataService.updateParentJobOutputs(subJobB);

        MvcResult mvcParentJobResponse = mockMvc.perform(get("/api/jobs/" + parentJob.getId() + "?projection=detailedJob")
                .header("REMOTE_USER", defaultUser.getName())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value("s2-cropper"))
            .andExpect(jsonPath("$.serviceId").value(parentJob.getConfig().getService().getId().toString()))
            .andExpect(jsonPath("$.id").value(parentJob.getId().toString()))
            .andExpect(jsonPath("$.phase").value("CREATED"))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.stage").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.extId").value(parentJob.getExtId()))
            .andExpect(jsonPath("$.parent").value(true))
                .andExpect(jsonPath("$.subJobStatusCounts").value(allOf(
                        aMapWithSize(1),
                        hasEntry("PENDING", 2))))
            .andReturn();
        String parentJobContent = mvcParentJobResponse.getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(parentJobContent, "$.outputFiles[*].filename"))
                .containsExactlyInAnyOrder("a.tif", "b.tif");
        assertThat(JsonPath.<List<String>>read(parentJobContent, "$.outputFiles[*]._links.download.href"))
                .containsExactlyInAnyOrder(
                        "http://localhost/api/jobs/" + subJobA.getId() + "/outputs/cropped_tif?filename=a.tif",
                        "http://localhost/api/jobs/" + subJobB.getId() + "/outputs/cropped_tif?filename=b.tif");

        mockMvc.perform(get("/api/jobs/" + subJobA.getId() + "?projection=detailedJob").header("REMOTE_USER", defaultUser.getName())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value("s2-cropper"))
            .andExpect(jsonPath("$.serviceId").value(subJobA.getConfig().getService().getId().toString()))
            .andExpect(jsonPath("$.id").value(subJobA.getId().toString()))
            .andExpect(jsonPath("$.phase").value("CREATED"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.stage").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.extId").value(subJobA.getExtId()))
            .andExpect(jsonPath("$.parent").value(false))
            .andExpect(jsonPath("$.outputFiles.length()").value(1))
            .andExpect(jsonPath("$.outputFiles[0].filename").value("a.tif"))
            .andExpect(jsonPath("$.outputFiles[0]._links.download.href")
                    .value("http://localhost/api/jobs/" + subJobA.getId() + "/outputs/cropped_tif?filename=a.tif"));

        mockMvc.perform(get("/api/jobs/" + subJobB.getId() + "?projection=detailedJob").header("REMOTE_USER", defaultUser.getName())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value("s2-cropper"))
            .andExpect(jsonPath("$.serviceId").value(subJobB.getConfig().getService().getId().toString()))
            .andExpect(jsonPath("$.id").value(subJobB.getId().toString()))
            .andExpect(jsonPath("$.phase").value("CREATED"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.stage").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.extId").value(subJobB.getExtId()))
            .andExpect(jsonPath("$.parent").value(false))
            .andExpect(jsonPath("$.outputFiles.length()").value(1))
            .andExpect(jsonPath("$.outputFiles[0].filename").value("b.tif"))
            .andExpect(jsonPath("$.outputFiles[0]._links.download.href")
                    .value("http://localhost/api/jobs/" + subJobB.getId() + "/outputs/cropped_tif?filename=b.tif"));
    }

    @Test
    public void testGETJobs_RetrieveJobs() throws Exception {

        OffsetDateTime startTest = OffsetDateTime.now(ZoneOffset.UTC);

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());


        MvcResult mvcJobsResponse = mockMvc.perform(get("/api/jobs").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andReturn();

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(2);

        Job job0 = allJobs.get(0);
        assertThat(job0.getOwner()).isEqualTo(defaultUser);
        assertThat(job0.getConfig()).isEqualTo(processingCoreTestDataService.findAllJobConfigs().get(0));
        assertThat(job0.getStage()).isNull();
        assertThat(job0.getStatus()).isEqualTo(Job.Status.PENDING);

        assertThat(job0.getCreated()).isBetween(startTest.minusMinutes(1), startTest.plusMinutes(1));
        assertThat(job0.getLastUpdated()).isBetween(startTest.minusMinutes(1), startTest.plusMinutes(1));
        assertThat(job0.getExtId()).isNotNull();
        assertThat(job0.isParent()).isFalse();

        Job job1 = allJobs.get(1);
        assertThat(job1.getOwner()).isEqualTo(defaultUser);
        assertThat(job1.getConfig()).isEqualTo(processingCoreTestDataService.findAllJobConfigs().get(0));
        assertThat(job1.getStage()).isNull();
        assertThat(job1.getStatus()).isEqualTo(Job.Status.PENDING);
        assertThat(job1.getCreated()).isBetween(startTest.minusMinutes(1), startTest.plusMinutes(1));
        assertThat(job1.getLastUpdated()).isBetween(startTest.minusMinutes(1), startTest.plusMinutes(1));

        assertThat(job1.getExtId()).isNotNull();
        assertThat(job1.isParent()).isFalse();

        String contentAsString = mvcJobsResponse.getResponse().getContentAsString();
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[0].id").toString()).isEqualTo(job0.getId().toString());
        assertThat((Object)JsonPath.read(contentAsString, "$._embedded.jobs[0].stage")).isNull();
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[0].status").toString()).isEqualTo(job0.getStatus().toString());
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[0].extId").toString()).isEqualTo(job0.getExtId());
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[0].serviceName").toString()).isEqualTo("s2-cropper");
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[0].serviceId").toString()).isEqualTo(job0.getConfig().getService().getId().toString());
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[1].id").toString()).isEqualTo(job1.getId().toString());
        assertThat((Object)JsonPath.read(contentAsString, "$._embedded.jobs[1].stage")).isNull();
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[1].status").toString()).isEqualTo(job1.getStatus().toString());
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[1].extId").toString()).isEqualTo(job1.getExtId());
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[1].serviceName").toString()).isEqualTo("s2-cropper");
        assertThat(JsonPath.read(contentAsString, "$._embedded.jobs[1].serviceId").toString()).isEqualTo(job1.getConfig().getService().getId().toString());

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);

        {
            job0 = allJobs.get(0);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job0.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job0.getExtId())
                        .setIntJobId(String.valueOf(job0.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job0.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }

        {
            job1 = allJobs.get(1);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job1.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job1.getExtId())
                        .setIntJobId(String.valueOf(job1.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job1.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }

    }

    @Test
    public void testGETParametricFind_RetrievesJobsByStatus_WhenStatusParameterIsProvided() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());


        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(2);

        Job jobUpdated = allJobs.get(0);
        jobUpdated.setStatus(Job.Status.COMPLETED);
        jobUpdated.setStage("SCROOLLING");
        jobUpdated.setPhase(JobStep.DATA_FETCH);
        jobUpdated = jobDataService.save(jobUpdated);

        mockMvc.perform(get("/api/jobs/search/parametricFind?status=COMPLETED").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs").isArray())
                .andExpect(jsonPath("$._embedded.jobs.length()").value(1))
                .andExpect(jsonPath("$._embedded.jobs[0].extId").value(jobUpdated.getExtId()))
                .andExpect(jsonPath("$._embedded.jobs[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$._embedded.jobs[0].stage").value("SCROOLLING"))
                .andExpect(jsonPath("$._embedded.jobs[0].phase").value("DATA_FETCH"))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceName").value("s2-cropper"))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceId").value(jobUpdated.getConfig().getService().getId().toString()))
                .andReturn();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);

        {
            Job job = allJobs.get(0);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }

        {
            Job job = allJobs.get(1);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }
    }

    @Test
    public void testGETParametricFind_RetrievesPagedJobs_WhenPaginationIsRequested() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(2);

        mockMvc.perform(get("/api/jobs/search/parametricFind?status=PENDING,RUNNING&page=0&size=1").header("REMOTE_USER", defaultUser.getName())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.jobs").isArray())
            .andExpect(jsonPath("$._embedded.jobs.length()").value(1))
            .andExpect(jsonPath("$._embedded.jobs[0].extId").value(allJobs.get(0).getExtId()))
            .andExpect(jsonPath("$._embedded.jobs[0].status").value("PENDING"))
            .andExpect(jsonPath("$._embedded.jobs[0].stage").isEmpty())
            .andExpect(jsonPath("$._embedded.jobs[0].phase").value("CREATED"))
            .andExpect(jsonPath("$._embedded.jobs[0].serviceName").value("s2-cropper"))
            .andExpect(jsonPath("$._embedded.jobs[0].serviceId").value(allJobs.get(0).getConfig().getService().getId().toString()))
            .andReturn();


        mockMvc.perform(get("/api/jobs/search/parametricFind?status=PENDING,RUNNING&page=1&size=1").header("REMOTE_USER", defaultUser.getName())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.jobs").isArray())
            .andExpect(jsonPath("$._embedded.jobs.length()").value(1))
            .andExpect(jsonPath("$._embedded.jobs[0].extId").value(allJobs.get(1).getExtId()))
            .andExpect(jsonPath("$._embedded.jobs[0].status").value("PENDING"))
            .andExpect(jsonPath("$._embedded.jobs[0].stage").isEmpty())
            .andExpect(jsonPath("$._embedded.jobs[0].phase").value("CREATED"))
            .andExpect(jsonPath("$._embedded.jobs[0].serviceName").value("s2-cropper"))
            .andExpect(jsonPath("$._embedded.jobs[0].serviceId").value(allJobs.get(1).getConfig().getService().getId().toString()))
            .andReturn();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);

        {
            Job job = allJobs.get(0);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }

        {
            Job job = allJobs.get(1);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }
    }

    @Test
    public void testNotExportedMethods_AreNotExposed() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        mockMvc.perform(post(mvcJobConfigResponse.getResponse().getHeader("Location") + "/launch")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(1);
        String jobLocation = "/api/jobs/" + allJobs.get(0).getId();

        // save(Job)
        mockMvc.perform(post("/api/jobs").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put(jobLocation).header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(patch(jobLocation).header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        // deleteById(Long) and delete(Job)
        mockMvc.perform(delete(jobLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // deleteAll(), deleteAll(Iterable) and deleteAllById(Iterable): no DELETE is mapped on the collection resource
        mockMvc.perform(delete("/api/jobs").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNotFound());

        // saveAll(Iterable), existsById(Long), findAllById(Iterable) and count() are not bound
        // to any endpoint by Spring Data REST, so there is no request to reject

        assertThat(processingCoreTestDataService.findAllJobs()).hasSize(1);

        {
            Job job = allJobs.get(0);

            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        }
    }

    @Test
    public void testGETParametricFind_RetrievesJobsIncludedBetweenStartAndEndTime_WhenStartDateTimeAndEndDateTimeAreProvided() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(3);

        // morningJob: 10:00 -> 12:00, afternoonJob: 13:00 -> 15:00, eveningJob: 16:00 -> 18:00
        Job morningJob = allJobs.get(0);
        morningJob.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        morningJob.setEndTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        morningJob = jobDataService.save(morningJob);

        Job afternoonJob = allJobs.get(1);
        afternoonJob.setStartTime(LocalDateTime.of(2026, 1, 1, 13, 0));
        afternoonJob.setEndTime(LocalDateTime.of(2026, 1, 1, 15, 0));
        afternoonJob = jobDataService.save(afternoonJob);

        Job eveningJob = allJobs.get(2);
        eveningJob.setStartTime(LocalDateTime.of(2026, 1, 1, 16, 0));
        eveningJob.setEndTime(LocalDateTime.of(2026, 1, 1, 18, 0));
        eveningJob = jobDataService.save(eveningJob);

        // 11:00 -> 14:00 overlaps morningJob and afternoonJob
        mockMvc.perform(get("/api/jobs/search/parametricFind?startDateTime=2026-01-01T11:00:00&endDateTime=2026-01-01T14:00:00").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs").isArray())
                .andExpect(jsonPath("$._embedded.jobs.length()").value(2))
                .andExpect(jsonPath("$._embedded.jobs[*].extId").value(Matchers.containsInAnyOrder(morningJob.getExtId(), afternoonJob.getExtId())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].id").value(Matchers.contains(morningJob.getId().intValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].status").value(Matchers.contains("PENDING")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].phase").value(Matchers.contains("CREATED")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].stage").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].startTime").value(Matchers.contains("2026-01-01T10:00:00")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].endTime").value(Matchers.contains("2026-01-01T12:00:00")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].parent").value(Matchers.contains(false)))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].serviceName").value(Matchers.contains("s2-cropper")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + morningJob.getExtId() + "')].serviceId").value(Matchers.contains(morningJob.getConfig().getService().getId().intValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].id").value(Matchers.contains(afternoonJob.getId().intValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].status").value(Matchers.contains("PENDING")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].phase").value(Matchers.contains("CREATED")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].stage").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].startTime").value(Matchers.contains("2026-01-01T13:00:00")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].endTime").value(Matchers.contains("2026-01-01T15:00:00")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].parent").value(Matchers.contains(false)))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].serviceName").value(Matchers.contains("s2-cropper")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + afternoonJob.getExtId() + "')].serviceId").value(Matchers.contains(afternoonJob.getConfig().getService().getId().intValue())))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andReturn();

        // 12:00 -> 13:00 touches the boundaries of morningJob and afternoonJob without overlapping them
        mockMvc.perform(get("/api/jobs/search/parametricFind?startDateTime=2026-01-01T12:00:00&endDateTime=2026-01-01T13:00:00").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs[*]").isEmpty())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0))
                .andExpect(jsonPath("$.page.number").value(0))
                .andReturn();

        // the time range filter is combined with the status filter
        morningJob.setStatus(Job.Status.COMPLETED);
        morningJob = jobDataService.save(morningJob);

        mockMvc.perform(get("/api/jobs/search/parametricFind?status=COMPLETED&startDateTime=2026-01-01T11:00:00&endDateTime=2026-01-01T14:00:00").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs").isArray())
                .andExpect(jsonPath("$._embedded.jobs.length()").value(1))
                .andExpect(jsonPath("$._embedded.jobs[0].id").value(morningJob.getId().toString()))
                .andExpect(jsonPath("$._embedded.jobs[0].extId").value(morningJob.getExtId()))
                .andExpect(jsonPath("$._embedded.jobs[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$._embedded.jobs[0].phase").value("CREATED"))
                .andExpect(jsonPath("$._embedded.jobs[0].stage").value(Matchers.nullValue()))
                .andExpect(jsonPath("$._embedded.jobs[0].startTime").value("2026-01-01T10:00:00"))
                .andExpect(jsonPath("$._embedded.jobs[0].endTime").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$._embedded.jobs[0].parent").value(false))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceName").value("s2-cropper"))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceId").value(morningJob.getConfig().getService().getId().toString()))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andReturn();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(3);


        assertThat(allJobs).allSatisfy(job -> {
            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build());
        });
    }

    @Test
    public void testGETParametricFind_RetrievesJobsByStartDateTimeIncludedBetweenStartAndEndTime_WhenOnlyStartDateTimeIsProvided() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(2);

        // runningJob: 10:00 -> 12:00, notRunningJob: 13:00 -> 15:00
        Job runningJob = allJobs.get(0);
        runningJob.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        runningJob.setEndTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        runningJob = jobDataService.save(runningJob);

        Job notRunningJob = allJobs.get(1);
        notRunningJob.setStartTime(LocalDateTime.of(2026, 1, 1, 13, 0));
        notRunningJob.setEndTime(LocalDateTime.of(2026, 1, 1, 15, 0));
        notRunningJob = jobDataService.save(notRunningJob);

        // 11:00 falls within runningJob only
        mockMvc.perform(get("/api/jobs/search/parametricFind?startDateTime=2026-01-01T11:00:00").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs").isArray())
                .andExpect(jsonPath("$._embedded.jobs.length()").value(1))
                .andExpect(jsonPath("$._embedded.jobs[0].id").value(runningJob.getId().toString()))
                .andExpect(jsonPath("$._embedded.jobs[0].extId").value(runningJob.getExtId()))
                .andExpect(jsonPath("$._embedded.jobs[0].status").value("PENDING"))
                .andExpect(jsonPath("$._embedded.jobs[0].phase").value("CREATED"))
                .andExpect(jsonPath("$._embedded.jobs[0].stage").value(Matchers.nullValue()))
                .andExpect(jsonPath("$._embedded.jobs[0].startTime").value("2026-01-01T10:00:00"))
                .andExpect(jsonPath("$._embedded.jobs[0].endTime").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$._embedded.jobs[0].parent").value(false))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceName").value("s2-cropper"))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceId").value(runningJob.getConfig().getService().getId().toString()))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andReturn();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);


        assertThat(allJobs).allSatisfy(job -> {
            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        });
    }

    @Test
    public void testGETParametricFind_RetrievesJobsByStartDateTimeIncludedBetweenStartAndEndTime_WhenOnlyEndDateTimeIsProvided() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(2);

        // runningJob: 10:00 -> 12:00, notRunningJob: 13:00 -> 15:00
        Job runningJob = allJobs.get(0);
        runningJob.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        runningJob.setEndTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        runningJob = jobDataService.save(runningJob);

        Job notRunningJob = allJobs.get(1);
        notRunningJob.setStartTime(LocalDateTime.of(2026, 1, 1, 13, 0));
        notRunningJob.setEndTime(LocalDateTime.of(2026, 1, 1, 15, 0));
        notRunningJob = jobDataService.save(notRunningJob);

        // 11:00 falls within runningJob only
        mockMvc.perform(get("/api/jobs/search/parametricFind?endDateTime=2026-01-01T11:00:00").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs").isArray())
                .andExpect(jsonPath("$._embedded.jobs.length()").value(1))
                .andExpect(jsonPath("$._embedded.jobs[0].id").value(runningJob.getId().toString()))
                .andExpect(jsonPath("$._embedded.jobs[0].extId").value(runningJob.getExtId()))
                .andExpect(jsonPath("$._embedded.jobs[0].status").value("PENDING"))
                .andExpect(jsonPath("$._embedded.jobs[0].phase").value("CREATED"))
                .andExpect(jsonPath("$._embedded.jobs[0].stage").value(Matchers.nullValue()))
                .andExpect(jsonPath("$._embedded.jobs[0].startTime").value("2026-01-01T10:00:00"))
                .andExpect(jsonPath("$._embedded.jobs[0].endTime").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$._embedded.jobs[0].parent").value(false))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceName").value("s2-cropper"))
                .andExpect(jsonPath("$._embedded.jobs[0].serviceId").value(runningJob.getConfig().getService().getId().toString()))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andReturn();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);

        assertThat(allJobs).allSatisfy(job -> {
            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        });
    }

    @Test
    public void testGETParametricFind_RetrievesAllJobs_WhenNoParameterIsProvided() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvcPlatformServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcPlatformServiceResponse.getResponse().getContentAsString()).isEmpty();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvcPlatformServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(mvcJobConfigResponse.getResponse().getContentAsString()).isEmpty();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(jobConfigLocation + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk());

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(2);

        // pendingJob: PENDING, 10:00 -> 12:00, completedJob: COMPLETED, no start/end time
        Job pendingJob = allJobs.get(0);
        pendingJob.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        pendingJob.setEndTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        pendingJob = jobDataService.save(pendingJob);

        Job completedJob = allJobs.get(1);
        completedJob.setStatus(Job.Status.COMPLETED);
        completedJob = jobDataService.save(completedJob);

        // without status, startDateTime and endDateTime no filter is applied
        mockMvc.perform(get("/api/jobs/search/parametricFind").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs").isArray())
                .andExpect(jsonPath("$._embedded.jobs.length()").value(2))
                .andExpect(jsonPath("$._embedded.jobs[*].extId").value(Matchers.containsInAnyOrder(pendingJob.getExtId(), completedJob.getExtId())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].id").value(Matchers.contains(pendingJob.getId().intValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].status").value(Matchers.contains("PENDING")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].phase").value(Matchers.contains("CREATED")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].stage").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].startTime").value(Matchers.contains("2026-01-01T10:00:00")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].endTime").value(Matchers.contains("2026-01-01T12:00:00")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].parent").value(Matchers.contains(false)))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].serviceName").value(Matchers.contains("s2-cropper")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + pendingJob.getExtId() + "')].serviceId").value(Matchers.contains(pendingJob.getConfig().getService().getId().intValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].id").value(Matchers.contains(completedJob.getId().intValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].status").value(Matchers.contains("COMPLETED")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].phase").value(Matchers.contains("CREATED")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].stage").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].startTime").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].endTime").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].parent").value(Matchers.contains(false)))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].serviceName").value(Matchers.contains("s2-cropper")))
                .andExpect(jsonPath("$._embedded.jobs[?(@.extId == '" + completedJob.getExtId() + "')].serviceId").value(Matchers.contains(completedJob.getConfig().getService().getId().intValue())))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andReturn();

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(2);

        assertThat(allJobs).allSatisfy(job -> {
            Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_EXECUTION);
            assertThat(message.getHeaders()).hasSize(1);
            assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));

            JobSpec jobSpec = (JobSpec) message.getPayload();
            assertThat(jobSpec).isEqualTo(
                JobSpec.newBuilder()
                    .setKind(Kind.WORKFLOW)
                    .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId(job.getExtId())
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId("default-user")
                        .setServiceId("s2-cropper")
                        .build())
                    .setService(S2_CROPPER_SERVICE.toBuilder().setId(String.valueOf(job.getConfig().getService().getId())).build())
                    .setResourceRequest(S2_CROPPER_RESOURCE_REQUEST)
                    .setDockerCommand("crop")
                    .addOutputs(CROPPED_TIF_OUTPUT)
                    .build()
            );
        });
    }

    private void initQueue() {
        queueService.sendObject(ProcessingCoreQueueNames.JOB_EXECUTION, "");
        queueService.receive(ProcessingCoreQueueNames.JOB_EXECUTION);

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isZero();
    }
}