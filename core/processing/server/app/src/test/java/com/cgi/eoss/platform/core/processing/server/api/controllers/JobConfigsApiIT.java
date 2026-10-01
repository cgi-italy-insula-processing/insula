package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.api.ApiConfig;
import com.cgi.eoss.platform.core.processing.server.api.ApiTestConfig;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
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
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.jayway.jsonpath.JsonPath;
import okhttp3.mockwebserver.MockWebServer;
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
import java.util.List;
import com.cgi.eoss.platform.rpc.Service;
import static com.cgi.eoss.platform.core.processing.server.api.controllers.ServicesApiIT.readAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ApiConfig.class, ApiTestConfig.class})
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-application.properties")
public class JobConfigsApiIT {

    private static final Path CWL_BASE_TEST_PATH = Paths.get("src", "test", "resources", "cwl");

    private static final String CWL_PATH = "/cwl/app-package.cwl";

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueService queueService;

    @Value("${platform.cwl.mockserver.port}")
    private Integer mockServerPort;

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
    public void testPOST_SavesJobConfig() throws Exception {

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

        List<JobConfig> allJobConfigs = processingCoreTestDataService.findAllJobConfigs();
        assertThat(allJobConfigs).hasSize(1);
        JobConfig jobConfig = allJobConfigs.get(0);
        assertThat(jobConfig.getOwner()).isEqualTo(defaultUser);
        assertThat(jobConfig.getLabel()).isNull();

        PlatformService service = jobConfig.getService();
        assertThat(service.getName()).isEqualTo("s2-cropper");
        assertThat(service.getOwner().getName()).isEqualTo(defaultUser.getName());
        assertThat(service.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        assertThat(service.getLicence()).isEqualTo(PlatformService.Licence.OPEN);
        assertThat(service.getStatus()).isEqualTo(PlatformService.Status.IN_DEVELOPMENT);
    }

    @Test
    public void testPOSTLaunch_LaunchesJob() throws Exception {

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

        List<JobConfig> allJobConfigs = processingCoreTestDataService.findAllJobConfigs();
        assertThat(allJobConfigs).hasSize(1);
        JobConfig jobConfig = allJobConfigs.get(0);
        assertThat(jobConfig.getOwner()).isEqualTo(defaultUser);
        assertThat(jobConfig.getLabel()).isNull();

        MvcResult mvcJobResponse = mockMvc.perform(post("/api/jobConfigs/" + jobConfig.getId() + "/launch").header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isOk())
                .andReturn();

        List<Job> allJobs = processingCoreTestDataService.findAllJobs();
        assertThat(allJobs).hasSize(1);

        Job job = allJobs.get(0);
        assertThat(job.getOwner()).isEqualTo(defaultUser);
        assertThat(job.getConfig()).isEqualTo(processingCoreTestDataService.findAllJobConfigs().get(0));
        assertThat(job.getStage()).isNull();
        assertThat(job.getStatus()).isEqualTo(Job.Status.PENDING);
        assertThat(job.getPhase()).isEqualTo(JobStep.CREATED);
        assertThat(job.getCreated()).isNotNull();
        assertThat(job.getLastUpdated()).isNotNull();
        assertThat(job.getExtId()).isNotNull();

        String contentAsString = mvcJobResponse.getResponse().getContentAsString();
        assertThat(JsonPath.read(contentAsString, "$.status").toString()).isEqualTo("PENDING");
        assertThat(JsonPath.read(contentAsString, "$.phase").toString()).isEqualTo("CREATED");
        assertThat((Object)JsonPath.read(contentAsString, "$.stage")).isNull();
        assertThat(JsonPath.read(contentAsString, "$.status").toString()).isEqualTo("PENDING");
        assertThat(JsonPath.read(contentAsString, "$.parent").toString()).isEqualTo("false");
        assertThat(JsonPath.read(contentAsString, "$.extId").toString()).isEqualTo(job.getExtId());
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isEqualTo(1);

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
                .setService(Service.newBuilder()
                    .setId(String.valueOf(job.getConfig().getService().getId()))
                    .setName("s2-cropper")
                    .setDockerImageTag("ogc-crop:0.1")
                    .setDescriptorType("CWL")
                    .build())
                .setResourceRequest(
                    ResourceRequest.newBuilder()
                        .setCpus("2.0")
                        .setRam("2Mi")
                        .setLimits(
                            ResourceSpec.newBuilder()
                                .setCpu("2.0")
                                .setRam("2Mi")
                                .build()
                        )
                        .build()
                )
                .setDockerCommand("crop")
                .addOutputs(
                    JobParam.newBuilder()
                        .setParamName("cropped_tif").setType("STAC")
                        .setOutputBinding(OutputBinding.newBuilder().setGlob(".").build())
                        .build()
                )
                .build()
        );
    }

    @Test
    public void testNotExportedMethods_AreNotExposed() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult mvsServiceResponse = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult mvcJobConfigResponse = mockMvc.perform(post("/api/jobConfigs")
                        .header("REMOTE_USER", defaultUser.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"" + mvsServiceResponse.getResponse().getHeader("Location") + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        String jobConfigLocation = mvcJobConfigResponse.getResponse().getHeader("Location");

        // findAll()
        mockMvc.perform(get("/api/jobConfigs").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(head("/api/jobConfigs").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // findAll(Sort)
        mockMvc.perform(get("/api/jobConfigs?sort=id,asc").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // findAll(Pageable)
        mockMvc.perform(get("/api/jobConfigs?page=0&size=10").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // deleteById(Long) and delete(JobConfig)
        mockMvc.perform(delete(jobConfigLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // deleteAll(), deleteAll(Iterable) and deleteAllById(Iterable): no DELETE is mapped on the collection resource
        mockMvc.perform(delete("/api/jobConfigs").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNotFound());

        assertThat(processingCoreTestDataService.findAllJobConfigs()).hasSize(1);
    }

    private void initQueue() {
        queueService.sendObject(ProcessingCoreQueueNames.JOB_EXECUTION, "");
        queueService.receive(ProcessingCoreQueueNames.JOB_EXECUTION);

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_EXECUTION)).isZero();
    }

}