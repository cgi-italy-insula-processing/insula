package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.codec.binary.Hex;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class ServiceDataServiceIT {

    @Autowired
    private ServiceDataService serviceDataService;
    @Autowired
    private UserDataService userDataService;
    @Autowired
    ServiceFileDataService serviceFileDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Before
    public void init() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @After
    public void shutdown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testSave_PersistsServiceWithDefaultGroupID_WhenGroupIDIsNotSet() {
        User owner = new User("owner-uid");
        userDataService.save(owner);

        PlatformService expectedService = new PlatformService();
        expectedService.setName("Test Service");
        expectedService.setOwner(owner);
        expectedService.setDockerTag("dockerTag");
        serviceDataService.save(expectedService);

        PlatformService retrievedService = serviceDataService.getById(expectedService.getId()).get();
        assertThat(retrievedService).isEqualTo(expectedService);
        assertThat(retrievedService.getGroupId()).isEqualTo(0L);
    }

    @Test
    public void testSave_PersistsServiceWithCustomGroupID_WhenGroupIDIsSet() {
        User owner = new User("owner-uid");
        userDataService.save(owner);


        PlatformService expectedService = new PlatformService();
        expectedService.setName("Test Service");
        expectedService.setOwner(owner);
        expectedService.setDockerTag("dockerTag");
        expectedService.setGroupId(100L);
        serviceDataService.save(expectedService);

        PlatformService retrievedService = serviceDataService.getById(expectedService.getId()).get();
        assertThat(retrievedService).isEqualTo(expectedService);
        assertThat(retrievedService.getGroupId()).isEqualTo(100L);
    }

    @Test
    public void testSave_PersistsServiceWithCwl_WhenCwlIsSet() {
        User owner = userDataService.save(new User("owner-uid"));
        PlatformService expectedService = new PlatformService();
        expectedService.setName("Test Service");
        expectedService.setOwner(owner);
        expectedService.setDockerTag("dockerTag");
        Cwl expectedCwl = new Cwl(URI.create("https://reference.url/cwl"), "document as free text");
        expectedService.setCwl(expectedCwl);
        PlatformService savedService = serviceDataService.save(expectedService);
        Long savedServiceId = savedService.getId();
        assertThat(savedServiceId).isNotNull();

        PlatformService retrievedService = serviceDataService.getById(savedServiceId).get();
        assertThat(retrievedService.getId()).isEqualTo(savedServiceId);
        assertThat(savedService).isEqualTo(retrievedService);
        assertThat(retrievedService.getCwl()).isEqualTo(expectedCwl);
        assertThat(retrievedService).isEqualTo(expectedService);
    }

    @Test
    public void testSave_PersistsServiceWithServiceDescriptor_WhenServiceDescriptorIsSet() {
        User owner = userDataService.save(new User("owner-uid"));
        PlatformService expectedService = new PlatformService();
        expectedService.setName("Test Service");
        expectedService.setOwner(owner);
        expectedService.setDockerTag("dockerTag");
        PlatformServiceDescriptor expectedServiceDescriptor = PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("inputId")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).prefix("--prefix").build())
                        .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoiInputRef").format("format").build())
                        .build()))
                .dataOutputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("outputId")
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("glob").build())
                        .build()))
                .serviceProvider("provider").serviceType("type").port("5800/tcp").id("Test Service")
                .environmentVariables(ImmutableMap.of("variableKey", "variableValue"))
                .dockerCommand("dockerCommand").dockerArguments(ImmutableList.of("arg1", "arg2")).title("Service Title").description("description")
                .version("1.0").metadata(ImmutableList.of("metadata1", "metadata2")).statusSupported(false).storeSupported(false).groupId(0L)
                .build();
        expectedService.setServiceDescriptor(expectedServiceDescriptor);
        PlatformService savedService = serviceDataService.save(expectedService);
        Long savedServiceId = savedService.getId();
        assertThat(savedServiceId).isNotNull();

        PlatformService retrievedService = serviceDataService.getById(savedServiceId).get();
        assertThat(retrievedService.getId()).isEqualTo(savedServiceId);
        assertThat(savedService).isEqualTo(retrievedService);

        PlatformServiceDescriptor retrievedServiceDescriptor = retrievedService.getServiceDescriptor();
        assertThat(retrievedServiceDescriptor.getServiceType()).isEqualTo("type");
        assertThat(retrievedServiceDescriptor.getServiceProvider()).isEqualTo("provider");
        assertThat(retrievedServiceDescriptor.getPort()).isEqualTo("5800/tcp");
        assertThat(retrievedServiceDescriptor.getDescription()).isEqualTo("description");
        assertThat(retrievedServiceDescriptor.getGroupId()).isEqualTo(0L);
        assertThat(retrievedServiceDescriptor.getTitle()).isEqualTo("Service Title");
        assertThat(retrievedServiceDescriptor.getVersion()).isEqualTo("1.0");
        assertThat(retrievedServiceDescriptor.getDockerCommand()).isEqualTo("dockerCommand");
        assertThat(retrievedServiceDescriptor.getId()).isEqualTo("Test Service");
        assertThat(retrievedServiceDescriptor.getDockerArguments()).isEqualTo(ImmutableList.of("arg1", "arg2"));
        assertThat(retrievedServiceDescriptor.getMetadata()).isEqualTo(ImmutableList.of("metadata1", "metadata2"));
        assertThat(retrievedServiceDescriptor.getEnvironmentVariables())
                .isEqualTo(ImmutableMap.of("variableKey", "variableValue"));
        assertThat(retrievedServiceDescriptor.getDataInputs()).isEqualTo(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("inputId")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).prefix("--prefix").build())
                        .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoiInputRef").format("format").build())
                        .build()));
        assertThat(retrievedServiceDescriptor.getDataOutputs()).isEqualTo(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("outputId")
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("glob").build()).build()));

    }


    @Test
    public void testFindByOwner_ReturnsServicesOwnedByGivenUser() {
        User owner = userDataService.save(new User("owner-uid"));
        PlatformService service = new PlatformService();
        service.setName("Service owned by user");
        service.setOwner(owner);
        service.setDockerTag("dockerTag");
        serviceDataService.save(service);

        User otherOwner = userDataService.save(new User("owner-uid-other"));
        PlatformService otherService = new PlatformService();
        otherService.setName("Service owned by someone else");
        otherService.setOwner(otherOwner);
        otherService.setDockerTag("dockerTag");
        serviceDataService.save(otherService);

        assertThat(serviceDataService.findByOwner(owner)).containsExactly(service);
        assertThat(serviceDataService.findByOwner(otherOwner)).containsExactly(otherService);
    }

    @Test
    public void testGetByName_ReturnsServiceByName_WhenServiceWithGivenNameExists() {
        PlatformService service = new PlatformService();
        String serviceName = "someName";
        service.setName(serviceName);
        service.setOwner(userDataService.save(new User("owner-uid")));
        service.setDockerTag("dockerTag");
        serviceDataService.save(service);

        assertThat(serviceDataService.getByName(serviceName).get()).isEqualTo(service);
    }

    @Test
    public void testGetByName_ReturnsServiceByName_WhenServiceWithGivenNameDoesNotExist() {
        PlatformService service = new PlatformService();
        service.setName("someName");
        service.setOwner(userDataService.save(new User("owner-uid")));
        service.setDockerTag("dockerTag");
        serviceDataService.save(service);

        assertThat(serviceDataService.getByName("someNotExistingName")).isNotPresent();
    }

    @Test
    public void testFindAllAvailable_ReturnsAllServicesWithStatusAvailable() {
        User owner = userDataService.save(new User("owner-uid"));
        PlatformService disabledService = new PlatformService();
        disabledService.setName("Disabled Service");
        disabledService.setOwner(owner);
        disabledService.setDockerTag("dockerTag");
        disabledService.setStatus(PlatformService.Status.DISABLED);
        serviceDataService.save(disabledService);
        PlatformService inDevelopmentService = new PlatformService();
        inDevelopmentService.setName("In development Service");
        inDevelopmentService.setOwner(owner);
        inDevelopmentService.setDockerTag("dockerTag");
        inDevelopmentService.setStatus(PlatformService.Status.IN_DEVELOPMENT);
        serviceDataService.save(inDevelopmentService);
        PlatformService availableServiceOne = new PlatformService();
        availableServiceOne.setName("Available Service One");
        availableServiceOne.setOwner(owner);
        availableServiceOne.setDockerTag("dockerTag");
        availableServiceOne.setStatus(PlatformService.Status.AVAILABLE);
        serviceDataService.save(availableServiceOne);
        PlatformService availableServiceTwo = new PlatformService();
        availableServiceTwo.setName("Available Service Two");
        availableServiceTwo.setOwner(owner);
        availableServiceTwo.setDockerTag("dockerTag");
        availableServiceTwo.setStatus(PlatformService.Status.AVAILABLE);
        serviceDataService.save(availableServiceTwo);

        assertThat(serviceDataService.findAllAvailable())
                .containsExactlyInAnyOrder(availableServiceOne, availableServiceTwo);

    }

    @Test
    public void testComputeServiceFingerprint_ComputesServiceFingerprintStringFromServiceFileFilenameAndContent_WhenServiceHasRelatedPlatformServiceFiles() throws NoSuchAlgorithmException {

        PlatformService service = new PlatformService();
        service.setName("Test Service");
        service.setOwner(userDataService.save(new User("owner-uid")));
        service.setDockerTag("dockerTag");
        serviceDataService.save(service);
        PlatformServiceContextFile serviceContextFile = serviceFileDataService.save(PlatformServiceContextFile.builder()
                .service(service)
                .content("someContent")
                .filename("someFilename.sh").build());

        String serviceFingerprint = serviceDataService.computeServiceFingerprint(service);

        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
        digest.update(serviceContextFile.getFilename().concat(serviceContextFile.getContent()).getBytes());
        String expectedMd5 = Hex.encodeHexString(digest.digest());
        assertThat(serviceFingerprint).isEqualTo(expectedMd5);
    }

    @Test
    public void testComputeServiceFingerprint_ComputesServiceFingerprintFromEmptyString_WhenServiceHasNoRelatedPlatformServiceFiles() throws NoSuchAlgorithmException {
        PlatformService service = new PlatformService();
        service.setName("Test Service");
        service.setOwner(userDataService.save(new User("owner-uid")));
        service.setDockerTag("dockerTag");
        serviceDataService.save(service);
        String serviceFingerprint = serviceDataService.computeServiceFingerprint(service);

        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
        digest.update("".getBytes());
        String expectedMd5 = Hex.encodeHexString(digest.digest());
        assertThat(serviceFingerprint).isEqualTo(expectedMd5);
    }

    @Test
    public void testGetPlatformDockerPrefix_ReturnsPlatformDockerPrefix() {

        assertThat(serviceDataService.getPlatformDockerPrefix()).isEqualTo("");
    }
}