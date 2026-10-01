package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Limits;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Requests;
import com.cgi.eoss.platform.core.processing.server.orchestrator.utils.CoreModelToGrpcUtils;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.SharedMemory;
import com.google.common.base.Strings;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds the gRPC {@link JobSpec} for a job from its configuration, inputs,
 * resource requirements and user mounts.
 */
public final class JobSpecMapper {

    private JobSpecMapper() {
    }

    /**
     * Creates and initializes a JobSpec.Builder for a job from its configuration, inputs and resource requirements,
     * adding the supplied user mounts.
     *
     * @param jobSubmissionRequest the request holding the job, its inputs and its resource requirements
     * @param userMounts the user mounts to add to the spec
     * @return a {@link JobSpec.Builder} populated from the request, with the default value Kind.WORKFLOW
     */
    public static JobSpec.Builder buildJobSpecBuilder(JobSubmissionRequest jobSubmissionRequest,
                                                      List<com.cgi.eoss.platform.rpc.UserMount> userMounts) {
        Job job = jobSubmissionRequest.getJob();
        PlatformService service = job.getConfig().getService();
        PlatformServiceDescriptor serviceDescriptor = service.getServiceDescriptor();
        List<JobParam> jobParams = createJobParams(serviceDescriptor, jobSubmissionRequest.getJobInputs());

        JobSpec.Builder jobSpecBuilder = JobSpec.newBuilder().setService(CoreModelToGrpcUtils.toRpcService(service))
                .setJob(CoreModelToGrpcUtils.toRpcJob(job)).addAllInputs(jobParams);

        if (service.getCwl() != null && serviceDescriptor != null) {
            addParametersFromCwl(jobSpecBuilder, serviceDescriptor);
        }

        Integer timeout = CorePlatformParameterExtractor.getTimeout(job);
        jobSpecBuilder.setTimeoutValue(timeout);

        jobSpecBuilder.addAllUserMount(userMounts);

        setResourceRequirements(jobSpecBuilder, service.getRequiredResources(), jobSubmissionRequest.getJobResourceRequirement());

        return jobSpecBuilder;
    }

    private static void addParametersFromCwl(JobSpec.Builder jobSpecBuilder, PlatformServiceDescriptor serviceDescriptor) {
        String dockerCommand = getDockerCommand(serviceDescriptor);
        List<String> dockerArguments = getDockerArguments(serviceDescriptor);
        jobSpecBuilder.setDockerCommand(dockerCommand).addAllDockerArguments(dockerArguments);

        jobSpecBuilder.addAllOutputs(JobParamsCreator.createOutputJobParams(serviceDescriptor.getDataOutputs()));

        Map<String, String> environmentVariables = getEnvironmentVariables(serviceDescriptor);
        jobSpecBuilder.putAllEnvironmentVariables(environmentVariables);
    }

    private static void setResourceRequirements(JobSpec.Builder jobSpecBuilder, PlatformServiceResources serviceResourceRequirement, JobResourceRequirement jobResourceRequirement) {
        ResourceRequest.Builder resourceRequestBuilder = ResourceRequest.newBuilder();

        mapStorage(resourceRequestBuilder, jobResourceRequirement.getStorage());
        mapGPUs(resourceRequestBuilder, jobResourceRequirement.getGpus());
        mapLimits(resourceRequestBuilder, jobResourceRequirement.getLimits());
        mapRequests(resourceRequestBuilder, jobResourceRequirement.getRequests());

        mapSharedMemory(resourceRequestBuilder, serviceResourceRequirement);

        jobSpecBuilder.setResourceRequest(resourceRequestBuilder.build());
    }

    private static String getDockerCommand(PlatformServiceDescriptor serviceDescriptor) {
        return serviceDescriptor.getDockerCommand() != null ? serviceDescriptor.getDockerCommand() : "";
    }

    private static List<String> getDockerArguments(PlatformServiceDescriptor serviceDescriptor) {
        return serviceDescriptor.getDockerArguments() != null ? serviceDescriptor.getDockerArguments() : Collections.emptyList();
    }

    private static Map<String, String> getEnvironmentVariables(PlatformServiceDescriptor serviceDescriptor) {
        return serviceDescriptor.getEnvironmentVariables() != null
                ? serviceDescriptor.getEnvironmentVariables()
                : Collections.emptyMap();
    }

    private static void mapStorage(ResourceRequest.Builder resourceRequestBuilder, Integer storageSize) {
        if (storageSize == null) {
            return;
        }
        resourceRequestBuilder.setStorage(storageSize);
    }

    private static void mapGPUs(ResourceRequest.Builder resourceRequestBuilder, Integer gpus) {
        if (gpus == null) {
            return;
        }
        resourceRequestBuilder.setGpus(String.valueOf(gpus));
    }

    private static void mapLimits(ResourceRequest.Builder resourceRequestBuilder, Limits limits) {
        if (limits == null) {
            return;
        }

        ResourceSpec.Builder resourceLimits = ResourceSpec.newBuilder();
        if (!Strings.isNullOrEmpty(limits.getRam())) {
            resourceLimits.setRam(limits.getRam());
        }
        if (!Strings.isNullOrEmpty(limits.getCpu())) {
            resourceLimits.setCpu(limits.getCpu());
        }

        resourceRequestBuilder.setLimits(resourceLimits.build());
    }

    private static void mapRequests(ResourceRequest.Builder resourceRequestBuilder, Requests requests) {
        if (requests == null) {
            return;
        }

        if (!Strings.isNullOrEmpty(requests.getRam())) {
            resourceRequestBuilder.setRam(requests.getRam());
        }
        if (!Strings.isNullOrEmpty(requests.getCpu())) {
            resourceRequestBuilder.setCpus(requests.getCpu());
        }
    }

    private static void mapSharedMemory(ResourceRequest.Builder resourceRequestBuilder, PlatformServiceResources platformServiceResources) {
        if (isNotSharedMemorySet(platformServiceResources)) {
            return;
        }
        resourceRequestBuilder.setSharedMemory(SharedMemory.newBuilder()
                .setSize(platformServiceResources.getSharedMemory().getSize()).build());
    }

    private static boolean isNotSharedMemorySet(PlatformServiceResources serviceResources) {
        if (serviceResources == null) {
            return true;
        }
        return serviceResources.getSharedMemory() == null || Strings.isNullOrEmpty(serviceResources.getSharedMemory().getSize());
    }

    private static List<JobParam> createJobParams(PlatformServiceDescriptor serviceDescriptor, JobInputs jobInputs) {
        return JobParamsCreator.createJobParams(
                serviceDescriptor != null ? serviceDescriptor.getDataInputs() : null,
                jobInputs
        );
    }

}
