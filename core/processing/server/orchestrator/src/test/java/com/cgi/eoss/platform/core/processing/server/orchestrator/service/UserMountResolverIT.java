package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreDefaultConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserMountDataService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreDefaultConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class UserMountResolverIT {

    @Autowired
    private UserMountResolver userMountResolver;

    @Autowired
    private UserMountDataService userMountDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    private User platformUser;

    @Before
    public void init() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        platformUser = userDataService.save(ProcessingCoreEntities.createUser().build());
    }

    @After
    public void shutdown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testResolve_ReturnsRpcUserMounts_WhenServiceHasAdditionalMounts() {

        UserMount userMount = new UserMount("bps-user-mount", "mountPath", UserMount.MountType.RO);
        userMount.setOwner(platformUser);
        userMount = userMountDataService.save(userMount);

        PlatformService service = new PlatformService("serviceName", platformUser, "dockerTag");
        Map<Long, String> additionalMounts = new HashMap<>();
        additionalMounts.put(userMount.getId(), "/home/workdir/mountpath");
        service.setAdditionalMounts(additionalMounts);

        List<com.cgi.eoss.platform.rpc.UserMount> rpcUserMounts = userMountResolver.resolve(service);

        assertThat(rpcUserMounts).containsExactly(com.cgi.eoss.platform.rpc.UserMount.newBuilder()
                .setName("bps-user-mount")
                .setMountPath("mountPath")
                .setType("ro")
                .setTargetPath("/home/workdir/mountpath")
                .build());
    }

    @Test
    public void testResolve_ReturnsEmptyList_WhenServiceHasNoAdditionalMounts() {

        PlatformService service = new PlatformService("serviceName", platformUser, "dockerTag");

        List<com.cgi.eoss.platform.rpc.UserMount> rpcUserMounts = userMountResolver.resolve(service);

        assertThat(rpcUserMounts).isEmpty();
    }

    @Test
    public void testResolve_ThrowsPlatformEntityNotFoundException_WhenAdditionalMountReferencesUnknownUserMount() {

        PlatformService service = new PlatformService("serviceName", platformUser, "dockerTag");
        Map<Long, String> additionalMounts = new HashMap<>();
        additionalMounts.put(424242L, "/home/workdir/mountpath");
        service.setAdditionalMounts(additionalMounts);

        assertThatThrownBy(() -> userMountResolver.resolve(service))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Failed to load User Mount with ID: 424242");
    }
}
