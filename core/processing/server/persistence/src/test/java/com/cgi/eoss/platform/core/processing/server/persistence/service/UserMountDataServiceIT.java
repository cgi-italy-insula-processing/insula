package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {PersistenceCoreConfig.class})
@TestPropertySource("classpath:test-persistence-core.properties")
public class UserMountDataServiceIT {

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private UserMountDataService userMountDataService;

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
    public void testSave_SavesEntity() {

        User owner = userDataService.save(ProcessingCoreEntities.createUser().build());
        UserMount userMount = new UserMount("MountName", "/mount/path", UserMount.MountType.RO);
        userMount.setOwner(owner);

        userMountDataService.save(userMount);

        List<UserMount> userMounts = userMountDataService.getAll();
        assertThat(userMounts).hasSize(1);

        assertThat(userMounts.get(0).getMountPath()).isEqualTo("/mount/path");
        assertThat(userMounts.get(0).getName()).isEqualTo("MountName");
        assertThat(userMounts.get(0).getType()).isEqualTo(UserMount.MountType.RO);
        assertThat(userMounts.get(0).getOwner()).isEqualTo(owner);
    }

    @Test
    public void testGetByName_RetrievesTheEntity() {

        User owner = userDataService.save(ProcessingCoreEntities.createUser().build());
        UserMount userMount = new UserMount("MountName", "/mount/path", UserMount.MountType.RO);
        userMount.setOwner(owner);

        userMountDataService.save(userMount);

        UserMount userMountByName = userMountDataService.getByName("MountName").get();

        assertThat(userMountByName.getMountPath()).isEqualTo("/mount/path");
        assertThat(userMountByName.getName()).isEqualTo("MountName");
        assertThat(userMountByName.getType()).isEqualTo(UserMount.MountType.RO);
        assertThat(userMountByName.getOwner()).isEqualTo(owner);
    }

    @Test
    public void testGetByName_ReturnsEmptyValue_WhenEntityDoesNotExistInDb() {
        assertThat(userMountDataService.getByName("notExist")).isEmpty();
    }

    @Test
    public void testSave_UpdateNewValues_WhenParametersAreChanged() {

        User owner = userDataService.save(ProcessingCoreEntities.createUser().build());
        UserMount userMount = new UserMount("MountName", "/mount/path", UserMount.MountType.RO);
        userMount.setOwner(owner);

        userMountDataService.save(userMount);

        List<UserMount> userMounts = userMountDataService.getAll();
        assertThat(userMounts).hasSize(1);

        assertThat(userMounts.get(0).getMountPath()).isEqualTo("/mount/path");
        assertThat(userMounts.get(0).getName()).isEqualTo("MountName");
        assertThat(userMounts.get(0).getType()).isEqualTo(UserMount.MountType.RO);
        assertThat(userMounts.get(0).getOwner()).isEqualTo(owner);

        UserMount userMount_2 = new UserMount("MountName", "/mount/path2", UserMount.MountType.RW);
        userMount_2.setOwner(owner);

        userMountDataService.save(userMount_2);
        userMounts = userMountDataService.getAll();
        assertThat(userMounts).hasSize(1);

        assertThat(userMounts.get(0).getMountPath()).isEqualTo("/mount/path2");
        assertThat(userMounts.get(0).getName()).isEqualTo("MountName");
        assertThat(userMounts.get(0).getType()).isEqualTo(UserMount.MountType.RW);
        assertThat(userMounts.get(0).getOwner()).isEqualTo(owner);
    }

    @Test
    public void testSave_ThrowsDataIntegrityViolationException_WhenNameUniqueIndexIsViolated() {

        User owner = userDataService.save(ProcessingCoreEntities.createUser().build());
        User notOwner = ProcessingCoreEntities.createUser().build();
        notOwner.setName("NotOwner");
        notOwner = userDataService.save(notOwner);

        UserMount userMount = new UserMount("MountName", "/mount/path", UserMount.MountType.RO);
        userMount.setOwner(owner);

        userMountDataService.save(userMount);

        List<UserMount> userMounts = userMountDataService.getAll();
        assertThat(userMounts).hasSize(1);

        assertThat(userMounts.get(0).getMountPath()).isEqualTo("/mount/path");
        assertThat(userMounts.get(0).getName()).isEqualTo("MountName");
        assertThat(userMounts.get(0).getType()).isEqualTo(UserMount.MountType.RO);
        assertThat(userMounts.get(0).getOwner()).isEqualTo(owner);

        UserMount userMount_2 = new UserMount("MountName", "/mount/path2", UserMount.MountType.RW);
        userMount_2.setOwner(notOwner);

        assertThatThrownBy(() -> userMountDataService.save(userMount_2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(" nested exception is org.hibernate.exception.ConstraintViolationException: could not execute statement");
    }
}