package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserDao;
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
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class ProcessingCoreDataInitializationManagedServiceIT {

    @Autowired
    private UserDao userDao;
    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private ApplicationContext applicationContext;

    @Before
    public void setUp() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @After
    public void shutDown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testProcessingCoreDataInitializationManagedService_CreatesDefaultUser_WhenAppContextIsRefreshedAndDefaultEntitiesDoNotExist() {

        // Publish a ContextRefreshedEvent in order to simulate the completion of the application startup process.
        applicationEventPublisher.publishEvent(new ContextRefreshedEvent(applicationContext));

        List<User> users = userDao.findAll();
        assertThat(users.size()).isEqualTo(1);
        User retrievedDefaultUser = users.get(0);
        assertThat(retrievedDefaultUser.getId()).isNotNull();
        assertThat(retrievedDefaultUser.getName()).isEqualTo("default-user");
        assertThat(retrievedDefaultUser.getEmail()).isEqualTo("default-user@test.dev");
        assertThat(retrievedDefaultUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    public void testProcessingCoreDataInitializationManagedService_DoesNotCreateDefaultUser_WhenAppContextIsRefreshedAndDefaultEntitiesExist() {

        User defaultUser = userDao.save(ProcessingCoreEntities.createUser()
                .name("default-user")
                .role(Role.USER)
                .build());

        // Publish a ContextRefreshedEvent in order to simulate the completion of the application startup process.
        applicationEventPublisher.publishEvent(new ContextRefreshedEvent(applicationContext));

        List<User> users = userDao.findAll();
        assertThat(users.size()).isEqualTo(1);
        User retrievedDefaultUser = users.get(0);
        assertThat(retrievedDefaultUser.getId()).isEqualTo(defaultUser.getId());
        assertThat(retrievedDefaultUser.getName()).isEqualTo("default-user");
        assertThat(retrievedDefaultUser.getRole()).isEqualTo(Role.USER);
        assertThat(retrievedDefaultUser.getEmail()).isNull();
    }


}