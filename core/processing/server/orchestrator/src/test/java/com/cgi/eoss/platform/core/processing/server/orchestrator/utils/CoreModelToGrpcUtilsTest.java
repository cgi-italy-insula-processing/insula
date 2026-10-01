package com.cgi.eoss.platform.core.processing.server.orchestrator.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.Test;

import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.rpc.Service;

public class CoreModelToGrpcUtilsTest {

    @Test
    public void testToRpcJob_MapsJobToRpcJob() {
        Job platformJob = new Job();
        platformJob.setExtId("extId");
        platformJob.setId(10L);
        platformJob.setOwner(new User("userName"));
        platformJob.setConfig(new JobConfig(platformJob.getOwner(),
                new PlatformService("serviceName", platformJob.getOwner(), "dockerTag")));

        com.cgi.eoss.platform.rpc.Job rpcJob = CoreModelToGrpcUtils.toRpcJob(platformJob);
        assertThat(rpcJob).isEqualTo(com.cgi.eoss.platform.rpc.Job.newBuilder()
                .setId("extId")
                .setIntJobId("10")
                .setUserId("userName")
                .setServiceId("serviceName")
                .build());

    }

    @Test
    public void testToRpcJob_MapsUserUuid_WhenJobOwnerContainsUuid() {
        Job platformJob = new Job();
        platformJob.setExtId("extId");
        platformJob.setId(10L);
        User owner = new User();
        owner.setName("userName");
        owner.setUuid("user_uuid");
        platformJob.setOwner(owner);
        platformJob.setConfig(new JobConfig(platformJob.getOwner(),
                new PlatformService("serviceName", platformJob.getOwner(), "dockerTag")));

        com.cgi.eoss.platform.rpc.Job rpcJob = CoreModelToGrpcUtils.toRpcJob(platformJob);
        assertThat(rpcJob).isEqualTo(com.cgi.eoss.platform.rpc.Job.newBuilder()
                .setId("extId")
                .setIntJobId("10")
                .setUserId("userName")
                .setServiceId("serviceName")
                .setUserUUID("user_uuid")
                .build());

    }

    @Test
    public void testToRpcService_MapsGroupId_WhenGroupIdIsProvided() {
        long groupId = 200L;
        Service actualService = CoreModelToGrpcUtils.toRpcService(
                ProcessingCoreEntities.createPlatformService(new User("user")).id(10L).groupId(groupId).build());

        assertThat(actualService).isEqualTo(Service.newBuilder()
                .setId("10")
                .setName("platform-service")
                .setDockerImageTag("dockerPrefix/dockerTag")
                .setGroupId(groupId)
                .build());
    }

    @Test
    public void testToRpcService_MapsDefaultGroupId_WhenGroupIdIsNotProvided() {
        Service actualService = CoreModelToGrpcUtils.toRpcService(
                ProcessingCoreEntities.createPlatformService(new User("user")).id(10L).groupId(null).build());

        assertThat(actualService).isEqualTo(Service.newBuilder()
                .setId("10")
                .setName("platform-service")
                .setDockerImageTag("dockerPrefix/dockerTag")
                .setGroupId(PlatformService.DEFAULT_GROUP_ID)
                .build());
    }

    @Test
    public void testToRpcService_MapsCwlDescriptorType_WhenCwlIsProvided() {
        Service actualService = CoreModelToGrpcUtils.toRpcService(
                ProcessingCoreEntities.createPlatformService(new User("user"))
                        .id(10L)
                        .cwl(new Cwl(URI.create("test://fake-cwl"), "a fake CWL document"))
                        .build());
        assertThat(actualService).isEqualTo(Service.newBuilder()
                .setId("10")
                .setName("platform-service")
                .setDockerImageTag("dockerPrefix/dockerTag")
                .setGroupId(100L)
                .setDescriptorType("CWL")
                .build());
    }

    @Test
    public void testToRpcService_MapsEmptyDescriptorType_WhenCwlIsNotProvided() {
        Service actualService = CoreModelToGrpcUtils.toRpcService(
                ProcessingCoreEntities.createPlatformService(new User("user")).id(10L).build());

        assertThat(actualService).isEqualTo(Service.newBuilder()
                .setId("10")
                .setName("platform-service")
                .setDockerImageTag("dockerPrefix/dockerTag")
                .setGroupId(100L)
                .setDescriptorType("")
                .build());
    }

    @Test
    public void testToRpcUserMount_MapsUserMountToRpcUserMount() {
        UserMount userMount = new UserMount("mountName", "/source", UserMount.MountType.RW);

        com.cgi.eoss.platform.rpc.UserMount actualUserMount = CoreModelToGrpcUtils.toRpcUserMount(userMount, "/target");

        assertThat(actualUserMount).isEqualTo(com.cgi.eoss.platform.rpc.UserMount.newBuilder()
                .setName("mountName")
                .setMountPath("/source")
                .setType("rw")
                .setTargetPath("/target")
                .build());
    }
}
