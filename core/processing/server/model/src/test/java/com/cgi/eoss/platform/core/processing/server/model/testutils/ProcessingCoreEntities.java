package com.cgi.eoss.platform.core.processing.server.model.testutils;

import com.cgi.eoss.platform.core.processing.server.model.CostQuotation;
import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDockerBuildInfo;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.google.common.collect.Multimap;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Convenience class that exposes methods to build processing core entities
 */
public class ProcessingCoreEntities {

    /**
     * Create a builder of User entities.
     * The User builder is initialized with a default name and with Role USER
     *
     * @return
     *         A builder of User entities initialized with default attributes
     */
    public static UserBuilder createUser() {
        return new UserBuilder()
                .name("platform-test-user")
                .role(Role.USER);
    }

    /**
     * Create a builder of PlatformService entities.
     * The PlatformService builder is initialized as available with the provided owner,
     * a default name and docker tag
     *
     * @param owner
     *            The service owner
     * @return
     *         A builder of PlatformService entities initialized with default attributes
     */
    public static PlatformServiceBuilder createPlatformService(User owner) {
        return new PlatformServiceBuilder()
                .owner(owner)
                .name("platform-service")
                .dockerTag("dockerPrefix/dockerTag")
                .status(PlatformService.Status.AVAILABLE)
                .groupId(100L);
    }

    /**
     * Create a builder of Job entities.
     * The Job builder is initialized with the provided owner and jobConfig and
     * with status CREATED
     *
     * @param owner
     *            The job owner
     * @param jobConfig
     *            The job configuration
     *
     * @return
     *         A builder of Job entities initialized with default attributes
     */
    public static JobBuilder createJob(User owner, JobConfig jobConfig) {
        return new JobBuilder()
                .owner(owner)
                .jobConfig(jobConfig)
                .extId(UUID.randomUUID().toString())
                .phase(JobStep.CREATED)
                .status(Job.Status.CREATED);
    }

    @Builder(builderMethodName = "")
    private static PlatformService createPlatformServiceInt(User owner, Long id, String name, String dockerTag,
                                                            PlatformService.Status status,
                                                            PlatformServiceDockerBuildInfo dockerBuildInfo,
                                                            PlatformServiceDescriptor platformServiceDescriptor,
                                                            Long groupId, Cwl cwl,
                                                            PlatformServiceResources platformServiceResources) {
        PlatformService platformService = new PlatformService(name, owner, dockerTag);
        platformService.setId(id);
        platformService.setStatus(status);
        platformService.setDockerBuildInfo(dockerBuildInfo);
        platformService.setServiceDescriptor(platformServiceDescriptor);
        platformService.setRequiredResources(platformServiceResources);
        platformService.setGroupId(groupId);
        platformService.setCwl(cwl);
        return platformService;
    }

    @Builder(builderMethodName = "")
    private static Job createJobInt(User owner, Long id, JobConfig jobConfig, Job.Status status,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    Multimap<String, String> outputs, CostQuotation costQuotation, String extId, Job parentJob, JobStep phase) {
        Job job = new Job(jobConfig, extId, owner);
        job.setId(id);
        job.setStatus(status);
        job.setStartTime(startTime);
        job.setEndTime(endTime);
        job.setOutputs(outputs);
        job.setCostQuotation(costQuotation);
        job.setParentJob(parentJob);
        job.setPhase(phase);
        return job;
    }

    @Builder(builderMethodName = "")
    private static User createUserInt(String name, Role role, String uuid) {
        User user = new User(name);
        user.setRole(role);
        user.setUuid(uuid);
        return user;
    }

}
