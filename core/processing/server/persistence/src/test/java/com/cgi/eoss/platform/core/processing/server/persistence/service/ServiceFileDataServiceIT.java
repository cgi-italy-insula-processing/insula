package com.cgi.eoss.platform.core.processing.server.persistence.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.google.common.collect.ImmutableSet;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class ServiceFileDataServiceIT {
    @Autowired
    private ServiceFileDataService dataService;
    @Autowired
    private ServiceDataService serviceDataService;
    @Autowired
    private UserDataService userService;

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
    public void test() throws Exception {
        User owner = userService.save(new User("owner-uid"));

        PlatformService svc = serviceDataService.save(new PlatformService("Test Service", owner, "dockerTag"));

        final String content = new String(Files.readAllBytes(Paths.get(getClass().getResource("/testService/Dockerfile").toURI())));
        final boolean executable = false;
        PlatformServiceContextFile serviceFile = dataService.save(new PlatformServiceContextFile(svc, "Dockerfile", executable, content));

        assertThat(dataService.getAll().size(), is(1));
        assertEqualsExcludingLazilyFetchedAssociations(dataService.getById(serviceFile.getId()).get(), serviceFile);

        List<PlatformServiceContextFile> retrievedFiles = dataService.getByIds(ImmutableSet.of(serviceFile.getId()));
        assertThat(retrievedFiles.size(), is(1));
        assertEqualsExcludingLazilyFetchedAssociations(retrievedFiles.get(0), serviceFile);
        assertThat(dataService.isUniqueAndValid(new PlatformServiceContextFile(svc, "Dockerfile")), is(false));
        assertThat(dataService.isUniqueAndValid(new PlatformServiceContextFile(svc, "Dockerfile2")), is(true));

        retrievedFiles = dataService.findByService(svc);
        assertThat(retrievedFiles.size(), is(1));
        assertEqualsExcludingLazilyFetchedAssociations(retrievedFiles.get(0), serviceFile);

        // Verify text file recovery
        assertThat(dataService.getById(serviceFile.getId()).get().getContent(), is(content));
    }

    @Test
    public void testServiceFingerprint() throws Exception {
        User owner = new User("owner-uid");
        User owner2 = new User("owner-uid2");
        userService.save(ImmutableSet.of(owner, owner2));

        PlatformService svc = new PlatformService();
        svc.setName("Test Service");
        svc.setOwner(owner);
        svc.setDockerTag("dockerTag");

        PlatformService svc2 = new PlatformService();
        svc2.setName("Test Service 2");
        svc2.setOwner(owner);
        svc2.setDockerTag("dockerTag");
        serviceDataService.save(svc);
        serviceDataService.save(svc2);

        String serviceFingerPrint = serviceDataService.computeServiceFingerprint(svc);

        assertThat(serviceFingerPrint, is(notNullValue()));

        byte[] fileBytes = Files.readAllBytes(Paths.get(getClass().getResource("/testService/Dockerfile").toURI()));
        PlatformServiceContextFile serviceFile = new PlatformServiceContextFile();
        serviceFile.setService(svc);
        serviceFile.setFilename("Dockerfile");
        serviceFile.setContent(new String(fileBytes));
        dataService.save(serviceFile);
        serviceDataService.save(svc);

        String newServiceFingerPrint = serviceDataService.computeServiceFingerprint(svc);
        assertThat(serviceFingerPrint, is(not(newServiceFingerPrint)));

        serviceFile.setService(svc2);
        dataService.save(serviceFile);

        serviceDataService.save(svc);

        String shouldBeRestoredServiceFingerPrint = serviceDataService.computeServiceFingerprint(svc);

        assertThat(serviceFingerPrint, is((shouldBeRestoredServiceFingerPrint)));

    }

    private static void assertEqualsExcludingLazilyFetchedAssociations(PlatformServiceContextFile actual, PlatformServiceContextFile expected) {
        assertThat(actual.getContent(), is(expected.getContent()));
        assertThat(actual.getFilename(), is(expected.getFilename()));
        assertThat(actual.getId(), is(expected.getId()));
    }
}