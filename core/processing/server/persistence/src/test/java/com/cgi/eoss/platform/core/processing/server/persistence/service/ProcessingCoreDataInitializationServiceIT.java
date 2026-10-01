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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class ProcessingCoreDataInitializationServiceIT {

    @Autowired
    private ProcessingCoreDataInitializationService processingCoreDataInitializationService;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

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
    public void testEnsureDefaultEntitiesExist_CreatesDefaultUser_WhenDefaultUserDoesNotExist () {

        processingCoreDataInitializationService.ensureDefaultEntitiesExist();

        List<User> users = userDao.findAll();
        assertThat(users.size()).isEqualTo(1);
        User retrievedDefaultUser = users.get(0);
        assertThat(retrievedDefaultUser.getId()).isNotNull();
        assertThat(retrievedDefaultUser.getName()).isEqualTo("default-user");
        assertThat(retrievedDefaultUser.getEmail()).isEqualTo("default-user@test.dev");
        assertThat(retrievedDefaultUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    public void testEnsureDefaultEntitiesExist_DoesNotCreateDefaultUser_WhenDefaultUserExists () {

        User defaultUser = createAndSaveDefaultUser();

        processingCoreDataInitializationService.ensureDefaultEntitiesExist();

        List<User> users = userDao.findAll();
        assertThat(users.size()).isEqualTo(1);
        User retrievedDefaultUser = users.get(0);
        assertThat(retrievedDefaultUser.getId()).isEqualTo(defaultUser.getId());
        assertThat(retrievedDefaultUser.getName()).isEqualTo("default-user");
        assertThat(retrievedDefaultUser.getRole()).isEqualTo(Role.USER);
        assertThat(retrievedDefaultUser.getEmail()).isNull();
    }

    @Test
    public void testGetDefaultUser_ReturnsDefaultUser_WhenDefaultUserExists() {

        User defaultUser = createAndSaveDefaultUser();

        User retrievedDefaultUser = processingCoreDataInitializationService.getDefaultUser();
        assertThat(retrievedDefaultUser.getId()).isEqualTo(defaultUser.getId());
        assertThat(retrievedDefaultUser.getName()).isEqualTo("default-user");
        assertThat(retrievedDefaultUser.getRole()).isEqualTo(Role.USER);
        assertThat(retrievedDefaultUser.getEmail()).isNull();
    }

    @Test
    public void testGetDefaultUser_ReturnsNull_WhenDefaultUserDoesNotExist() {

        assertThat(processingCoreDataInitializationService.getDefaultUser()).isNull();
    }

    @Test
    public void testGetDefaultUserName_ReturnsDefaultUsername() {

        assertThat(processingCoreDataInitializationService.getDefaultUserName()).isEqualTo("default-user");
    }

    @Test
    public void testGetDefaultAdmin_ReturnsDefaultUser_WhenDefaultUserExists() {

        User defaultUser = createAndSaveDefaultUser();

        User retrievedDefaultUser = processingCoreDataInitializationService.getDefaultAdmin();
        assertThat(retrievedDefaultUser.getId()).isEqualTo(defaultUser.getId());
        assertThat(retrievedDefaultUser.getName()).isEqualTo("default-user");
        assertThat(retrievedDefaultUser.getRole()).isEqualTo(Role.USER);
        assertThat(retrievedDefaultUser.getEmail()).isNull();

    }

    @Test
    public void testGetDefaultAdmin_ReturnsNull_WhenDefaultUserDoesNotExist() {

        assertThat(processingCoreDataInitializationService.getDefaultAdmin()).isNull();
    }

    @Test
    public void testGetPlatformDockerPrefix_ReturnsEmptyString() {

        assertThat(processingCoreDataInitializationService.getPlatformDockerPrefix()).isEqualTo("");
    }

    private User createAndSaveDefaultUser() {
        return userDao.save(ProcessingCoreEntities.createUser()
                .name("default-user")
                .role(Role.USER)
                .build());
    }
}