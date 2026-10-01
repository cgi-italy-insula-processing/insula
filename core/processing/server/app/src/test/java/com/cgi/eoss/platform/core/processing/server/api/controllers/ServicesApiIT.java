package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.api.ApiConfig;
import com.cgi.eoss.platform.core.processing.server.api.ApiTestConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;

import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(classes = {ApiConfig.class, ApiTestConfig.class})
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-application.properties")
public class ServicesApiIT {

    private static final Path REST_SERVICES_BASE_TEST_PATH = Paths.get("src", "test", "resources", "services");
    private static final Path CWL_BASE_TEST_PATH = Paths.get("src", "test", "resources", "cwl");

    private static final String CWL_PATH = "/cwl/app-package.cwl";
    private static final String CWL_UPDATED_PATH = "/cwl/app-package-updated.cwl";

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceDataService serviceDataService;

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

    }

    @Test
    public void testPost_CreatesPlatformService() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated());

        List<PlatformService> allPlatformServices = processingCoreTestDataService.findAllPlatformServices();
        assertThat(allPlatformServices).hasSize(1);

        PlatformService platformService = allPlatformServices.get(0);
        assertThat(platformService.getName()).isEqualTo("s2-cropper");
        assertThat(platformService.getOwner().getName()).isEqualTo(defaultUser.getName());
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        assertThat(platformService.getLicence()).isEqualTo(PlatformService.Licence.OPEN);
        assertThat(platformService.getStatus()).isEqualTo(PlatformService.Status.IN_DEVELOPMENT);
        assertThat(platformService.getDockerTag()).isEqualTo("ogc-crop:0.1");
    }

    @Test
    public void testPost_ReturnsBadRequest_WhenProcessingServicePostedIsNotCWL() throws Exception {

        String content = readAsString(REST_SERVICES_BASE_TEST_PATH.resolve("post-service.json"));

        mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content(content))
                .andExpect(status().isBadRequest())
                .andReturn();
    }

    @Test
    public void testGet_RetrievesPlatformServiceFromHeaderLocation() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult result = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get(result.getResponse().getHeader("Location")).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("s2-cropper"))
                .andExpect(jsonPath("$.status").value("IN_DEVELOPMENT"))
                .andExpect(jsonPath("$.type").value("PROCESSOR"))
                .andExpect(jsonPath("$.licence").value("OPEN"))
                .andExpect(jsonPath("$.dockerTag").value("ogc-crop:0.1"));
    }

    @Test
    public void testGet_RetrievesPlatformService_WhenServiceExist() throws Exception {

        PlatformService platformServiceSaved;

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 2");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.DISABLED);
            platformService.setLicence(PlatformService.Licence.RESTRICTED);

            platformServiceSaved = serviceDataService.save(platformService);
        }

        String serviceLocation = "/api/services/" + platformServiceSaved.getId();

        mockMvc.perform(get(serviceLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Service 2"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.type").value("BULK_PROCESSOR"))
                .andExpect(jsonPath("$.licence").value("RESTRICTED"))
                .andExpect(jsonPath("$.dockerTag").value("dockerTag"));

        List<PlatformService> allPlatformServices = processingCoreTestDataService.findAllPlatformServices();

        assertThat(allPlatformServices).hasSize(1);

        PlatformService platformService = allPlatformServices.get(0);
        assertThat(platformService.getName()).isEqualTo("Test Service 2");
        assertThat(platformService.getOwner().getName()).isEqualTo(defaultUser.getName());
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.BULK_PROCESSOR);
        assertThat(platformService.getLicence()).isEqualTo(PlatformService.Licence.RESTRICTED);
        assertThat(platformService.getStatus()).isEqualTo(PlatformService.Status.DISABLED);

    }

    @Test
    public void testGet_ReturnsNotFound_WhenPlatformServiceRequestedDoesNotExist() throws Exception {

        mockMvc.perform(get("/api/services/42").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testPost_DisablesPlatformService() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );

        MvcResult result = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(post(result.getResponse().getHeader("Location") + "/disable").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk());

        List<PlatformService> allPlatformServices = processingCoreTestDataService.findAllPlatformServices();
        assertThat(allPlatformServices).hasSize(1);
        assertThat(allPlatformServices.get(0).getStatus()).isEqualTo(PlatformService.Status.DISABLED);
    }

    @Test
    public void testParametricFind_ReturnsListOfPlatformService_WhenStatusRequestedMatchesWithASingleRequest() throws Exception {

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 1");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag1");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.DISABLED);
            platformService.setLicence(PlatformService.Licence.RESTRICTED);

            serviceDataService.save(platformService);
        }

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 2");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag2");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.IN_DEVELOPMENT);
            platformService.setLicence(PlatformService.Licence.RESTRICTED);

            serviceDataService.save(platformService);
        }

        String parametricFindLocation = "/api/services/search/parametricFind?status=DISABLED";
        mockMvc.perform(get(parametricFindLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.services").isArray())
                .andExpect(jsonPath("$._embedded.services.length()").value(1))
                .andExpect(jsonPath("$._embedded.services[0].name").value("Test Service 1"))
                .andExpect(jsonPath("$._embedded.services[0].type").value("BULK_PROCESSOR"))
                .andExpect(jsonPath("$._embedded.services[0].status").value("DISABLED"))
                .andExpect(jsonPath("$._embedded.services[0].dockerTag").value("dockerTag1"));
    }

    @Test
    public void testParametricFind_ReturnsListOfPlatformService_WhenStatusRequestedMatchesWithMultipleRequest() throws Exception {

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 1");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag1");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.DISABLED);
            platformService.setLicence(PlatformService.Licence.RESTRICTED);

            serviceDataService.save(platformService);
        }

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 2");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag2");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.IN_DEVELOPMENT);
            platformService.setLicence(PlatformService.Licence.OPEN);

            serviceDataService.save(platformService);
        }

        String parametricFindLocation = "/api/services/search/parametricFind?status=DISABLED,IN_DEVELOPMENT";
        mockMvc.perform(get(parametricFindLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.services").isArray())
                .andExpect(jsonPath("$._embedded.services.length()").value(2))
                .andExpect(jsonPath("$._embedded.services[0].name").value("Test Service 1"))
                .andExpect(jsonPath("$._embedded.services[0].type").value("BULK_PROCESSOR"))
                .andExpect(jsonPath("$._embedded.services[0].status").value("DISABLED"))
                .andExpect(jsonPath("$._embedded.services[0].dockerTag").value("dockerTag1"))
                .andExpect(jsonPath("$._embedded.services[0].licence").value("RESTRICTED"))

                .andExpect(jsonPath("$._embedded.services[1].name").value("Test Service 2"))
                .andExpect(jsonPath("$._embedded.services[1].type").value("BULK_PROCESSOR"))
                .andExpect(jsonPath("$._embedded.services[1].status").value("IN_DEVELOPMENT"))
                .andExpect(jsonPath("$._embedded.services[1].dockerTag").value("dockerTag2"))
                .andExpect(jsonPath("$._embedded.services[1].licence").value("OPEN"));
    }

    @Test
    public void testParametricFind_ReturnsPagedListOfPlatformServicePaged_WhenStatusRequestedMatchesWithMultipleRequest() throws Exception {

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 1");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag1");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.DISABLED);
            platformService.setLicence(PlatformService.Licence.RESTRICTED);

            serviceDataService.save(platformService);
        }

        {
            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 2");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag2");
            platformService.setType(PlatformService.Type.BULK_PROCESSOR);
            platformService.setStatus(PlatformService.Status.IN_DEVELOPMENT);
            platformService.setLicence(PlatformService.Licence.OPEN);

            serviceDataService.save(platformService);
        }

        String parametricFindLocationPage1 = "/api/services/search/parametricFind?status=DISABLED,IN_DEVELOPMENT&page=0&size=1";
        mockMvc.perform(get(parametricFindLocationPage1).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.services").isArray())
                .andExpect(jsonPath("$._embedded.services.length()").value(1))
                .andExpect(jsonPath("$._embedded.services[0].name").value("Test Service 1"))
                .andExpect(jsonPath("$._embedded.services[0].type").value("BULK_PROCESSOR"))
                .andExpect(jsonPath("$._embedded.services[0].status").value("DISABLED"))
                .andExpect(jsonPath("$._embedded.services[0].dockerTag").value("dockerTag1"))
                .andExpect(jsonPath("$._embedded.services[0].licence").value("RESTRICTED"));


        String parametricFindLocationPage2 = "/api/services/search/parametricFind?status=DISABLED,IN_DEVELOPMENT&page=1&size=1";
        mockMvc.perform(get(parametricFindLocationPage2).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.services").isArray())
                .andExpect(jsonPath("$._embedded.services.length()").value(1))
                .andExpect(jsonPath("$._embedded.services[0].name").value("Test Service 2"))
                .andExpect(jsonPath("$._embedded.services[0].type").value("BULK_PROCESSOR"))
                .andExpect(jsonPath("$._embedded.services[0].status").value("IN_DEVELOPMENT"))
                .andExpect(jsonPath("$._embedded.services[0].dockerTag").value("dockerTag2"))
                .andExpect(jsonPath("$._embedded.services[0].licence").value("OPEN"));

        String parametricFindLocationPage3 = "/api/services/search/parametricFind?status=DISABLED,IN_DEVELOPMENT&page=2&size=1";
        mockMvc.perform(get(parametricFindLocationPage3).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.services").isArray())
                .andExpect(jsonPath("$._embedded.services.length()").value(0))
        ;
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

        String serviceLocation = mvcPlatformServiceResponse.getResponse().getHeader("Location");

        // findAll()
        mockMvc.perform(get("/api/services").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(head("/api/services").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // findAll(Sort)
        mockMvc.perform(get("/api/services?sort=id,asc").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // findAll(Pageable)
        mockMvc.perform(get("/api/services?page=0&size=10").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // deleteById(Long) and delete(PlatformService)
        mockMvc.perform(delete(serviceLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isMethodNotAllowed());

        // deleteAll(), deleteAll(Iterable) and deleteAllById(Iterable): no DELETE is mapped on the collection resource
        mockMvc.perform(delete("/api/services").header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isNotFound());

        assertThat(processingCoreTestDataService.findAllPlatformServices()).hasSize(1);
    }

    @Test
    public void testGet_RetrievesPlatformService_WhenDetailedPlatformServiceProjectionIsRequested() throws Exception {

        PlatformService platformServiceSaved;
        {
            Map<String, String> defaultAttrs = new HashMap<>();
            defaultAttrs.put("dataType", "string");

            Map<String, String> platformMetadata = new HashMap<>();
            platformMetadata.put("preventUrlDownload", "false");
            platformMetadata.put("format", "CATALOGUE");
            platformMetadata.put("type", "STAC");

            PlatformServiceDescriptor.Parameter inputParameter = new PlatformServiceDescriptor.Parameter();
            inputParameter.setId("in");
            inputParameter.setTitle("in");
            inputParameter.setMinOccurs(1);
            inputParameter.setMaxOccurs(1);
            inputParameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
            inputParameter.setDefaultAttrs(defaultAttrs);
            inputParameter.setSupportedAttrs(new ArrayList<>());
            inputParameter.setPlatformMetadata(platformMetadata);
            inputParameter.setInputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).build());

            PlatformServiceDescriptor.Parameter outputParameter = new PlatformServiceDescriptor.Parameter();
            outputParameter.setId("out");
            outputParameter.setMinOccurs(1);
            outputParameter.setMaxOccurs(1);
            outputParameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
            outputParameter.setDefaultAttrs(defaultAttrs);
            outputParameter.setSupportedAttrs(new ArrayList<>());
            outputParameter.setPlatformMetadata(platformMetadata);
            outputParameter.setOutputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("./outDir/out").build());

            PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
            serviceDescriptor.setId("service-descriptor-1");
            serviceDescriptor.setTitle("Service Descriptor 1");
            serviceDescriptor.setDataInputs(Collections.singletonList(inputParameter));
            serviceDescriptor.setDataOutputs(Collections.singletonList(outputParameter));

            PlatformService platformService = new PlatformService();
            platformService.setName("Test Service 2");
            platformService.setOwner(userDataService.getDefaultUser());
            platformService.setDockerTag("dockerTag");
            platformService.setType(PlatformService.Type.PROCESSOR);
            platformService.setStatus(PlatformService.Status.AVAILABLE);
            platformService.setLicence(PlatformService.Licence.OPEN);
            platformService.setServiceDescriptor(serviceDescriptor);

            platformServiceSaved = serviceDataService.save(platformService);
        }

        String serviceLocation = "/api/services/" + platformServiceSaved.getId() + "?projection=detailedPlatformService";

        mockMvc.perform(get(serviceLocation).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Service 2"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.type").value("PROCESSOR"))
                .andExpect(jsonPath("$.licence").value("OPEN"))
                .andExpect(jsonPath("$.dockerTag").value("dockerTag"))
                .andExpect(jsonPath("$.serviceDescriptor.id").value("service-descriptor-1"))
                .andExpect(jsonPath("$.serviceDescriptor.title").value("Service Descriptor 1"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].id").value("in"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].title").value("in"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].minOccurs").value("1"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].maxOccurs").value("1"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].data").value("LITERAL"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].defaultAttrs.dataType").value("string"))
                .andExpect(jsonPath("$.serviceDescriptor.dataInputs[0].platformMetadata.format").value("CATALOGUE"))
                .andExpect(jsonPath("$.serviceDescriptor.dataOutputs[0].id").value("out"))
                .andExpect(jsonPath("$.serviceDescriptor.dataOutputs[0].minOccurs").value("1"))
                .andExpect(jsonPath("$.serviceDescriptor.dataOutputs[0].maxOccurs").value("1"))
                .andExpect(jsonPath("$.serviceDescriptor.dataOutputs[0].data").value("LITERAL"));
    }

    @Test
    public void testPut_UpdatesPlatformService_WhenUpdatedThroughCwl() throws Exception {

        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package.cwl")))
        );
        webServer.expect(
                MockWebServerWrapper.request(HttpMethod.GET, CWL_UPDATED_PATH),
                MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, readAsString(CWL_BASE_TEST_PATH.resolve("app-package-updated.cwl")))
        );

        String cwlUpdatedUrl = webServer.unwrap().url(CWL_UPDATED_PATH).toString();

        MvcResult result = mockMvc.perform(post("/api/services").header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUrl + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(put(result.getResponse().getHeader("Location")).header("REMOTE_USER", defaultUser.getName())
                        .content("{\"cwl\": {\"url\": \"" + cwlUpdatedUrl + "\"}}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(result.getResponse().getHeader("Location")).header("REMOTE_USER", defaultUser.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("s2-cropper"))
                .andExpect(jsonPath("$.status").value("IN_DEVELOPMENT"))
                .andExpect(jsonPath("$.dockerTag").value("ogc-crop:0.1"))
                .andExpect(jsonPath("$.description").value("This application crops a Sentinel-2 band - updated version"))
                .andExpect(jsonPath("$.serviceDescriptor.version").value("1.1.0"))
                .andExpect(jsonPath("$.cwl.url").value(cwlUpdatedUrl));

        List<PlatformService> allPlatformServices = processingCoreTestDataService.findAllPlatformServices();
        assertThat(allPlatformServices).hasSize(1);
    }

    public static String readAsString(Path path) {
        try {
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}