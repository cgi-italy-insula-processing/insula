package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class UserDataServiceIT {

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private ApplicationContext applicationContext;

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
    public void testIsUniqueAndValid_ReturnsTrue_WhenNoUserWithSameUsernameExists() {

        userDataService.save(new User("owner-uid"));
        assertThat(userDataService.isUniqueAndValid(new User("owner-uid2"))).isTrue();
    }

    @Test
    public void testIsUniqueAndValid_ReturnsFalse_WhenAnUserWithSameUsernameExists() {

        userDataService.save(new User("owner-uid"));
        assertThat(userDataService.isUniqueAndValid(new User("owner-uid"))).isFalse();
    }

    @Test
    public void testGetDefaultUserName_ReturnsDefaultUserName() {

        assertThat(userDataService.getDefaultUserName()).isEqualTo("default-user");
    }

    @Test
    public void testGetDefaultUser_ReturnsTheUserEntityCreatedAfterApplicationStartup() {

        // Publish a ContextRefreshedEvent in order to simulate the completion of the application startup process.
        applicationEventPublisher.publishEvent(new ContextRefreshedEvent(applicationContext));

        User defaultUser = userDataService.getDefaultUser();
        assertThat(defaultUser.getId()).isNotNull();
        assertThat(defaultUser.getName()).isEqualTo("default-user");
        assertThat(defaultUser.getEmail()).isEqualTo("default-user@test.dev");
        assertThat(defaultUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    public void testGetDefaultAdmin_ReturnsTheUserEntityCreatedAfterApplicationStartup() {

        // Publish a ContextRefreshedEvent in order to simulate the completion of the application startup process.
        applicationEventPublisher.publishEvent(new ContextRefreshedEvent(applicationContext));

        User defaultAdmin = userDataService.getDefaultAdmin();
        assertThat(defaultAdmin.getId()).isNotNull();
        assertThat(defaultAdmin.getName()).isEqualTo("default-user");
        assertThat(defaultAdmin.getEmail()).isEqualTo("default-user@test.dev");
        assertThat(defaultAdmin.getRole()).isEqualTo(Role.USER);

    }
}
