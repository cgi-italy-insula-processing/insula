package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.User;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Limits;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Requests;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.SharedMemory;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.cgi.eoss.platform.rpc.UserMount;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JobSpecMapperTest {

    private final User owner = new User("platform-test-user");

    @Test
    public void testBuildJobSpecBuilder_AddsCwlParametersAndOutputs_WhenCwlIsSet() {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setCwl(new Cwl(URI.create("platform://fake-url"), "document"));
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataOutputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("output-with-binding")
                        .platformMetadata(ImmutableMap.of("preventUrlDownload", "false", "type", "STAC"))
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("./test-glob").build())
                        .build()))
                .dockerCommand("dockerCommand")
                .dockerArguments(ImmutableList.of("arg1", "arg2"))
                .environmentVariables(ImmutableMap.of("variableKey", "variableValue"))
                .build());

        Job job = createJob(service);

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, JobInputs.builder().jobId(job.getExtId()).inputs(Collections.emptyMap()).build(),
                        JobResourceRequirement.builder().storage(10240).gpus(5).build()),
                Collections.emptyList()).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("CWL")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .addAllOutputs(ImmutableList.of(
                        JobParam.newBuilder().setParamName("output-with-binding")
                                .setType("STAC")
                                .setOutputBinding(OutputBinding.newBuilder().setGlob("./test-glob").build())
                                .build()))
                .setDockerCommand("dockerCommand")
                .addAllDockerArguments(ImmutableList.of("arg1", "arg2"))
                .putAllEnvironmentVariables(ImmutableMap.of("variableKey", "variableValue"))
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .setKind(Kind.WORKFLOW)
                .build());
    }

    @Test
    public void testBuildJobSpecBuilder_OmitsDockerCommandAndDockerArgumentsAndEnvironmentVariables_WhenCwlIsNotSet() {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dockerCommand("dockerCommand")
                .dockerArguments(ImmutableList.of("arg1", "arg2"))
                .environmentVariables(ImmutableMap.of("variableKey", "variableValue"))
                .build());

        Job job = createJob(service);

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, JobInputs.builder().jobId(job.getExtId()).inputs(Collections.emptyMap()).build(),
                        JobResourceRequirement.builder().storage(10240).gpus(5).build()),
                Collections.emptyList()).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .setKind(Kind.WORKFLOW)
                .build());
    }

    @Test
    public void testBuildJobSpecBuilder_AddsResolvedUserMounts_WhenPassedUserMountsAreNotEmpty() {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setCwl(new Cwl(URI.create("platform://fake-url"), "document"));
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataOutputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("output-with-binding")
                        .platformMetadata(Collections.emptyMap())
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("./test-glob").build())
                        .build()))
                .dockerCommand("dockerCommand")
                .dockerArguments(ImmutableList.of("arg1", "arg2"))
                .environmentVariables(ImmutableMap.of("variableKey", "variableValue"))
                .build());

        Job job = createJob(service);

        UserMount userMount = UserMount.newBuilder()
                .setName("bps-user-mount")
                .setMountPath("mountPath")
                .setType("ro")
                .setTargetPath("/home/workdir/mountpath")
                .build();

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, JobInputs.builder().jobId(job.getExtId()).inputs(Collections.emptyMap()).build(),
                        JobResourceRequirement.builder().storage(10240).gpus(5).build()),
                ImmutableList.of(userMount)).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("CWL")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .addAllOutputs(ImmutableList.of(
                        JobParam.newBuilder().setParamName("output-with-binding")
                                .setType("URL")
                                .setOutputBinding(OutputBinding.newBuilder().setGlob("./test-glob").build())
                                .build()))
                .addAllUserMount(ImmutableList.of(userMount))
                .setDockerCommand("dockerCommand")
                .addAllDockerArguments(ImmutableList.of("arg1", "arg2"))
                .putAllEnvironmentVariables(ImmutableMap.of("variableKey", "variableValue"))
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .setKind(Kind.WORKFLOW)
                .build());
    }

    @Test
    public void testBuildJobSpecBuilder_MapsStacInputAsCatalogUrl_WhenJobInputsContainStacInput() throws Exception {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("stacInput")
                        .title("stacInput")
                        .platformMetadata(ImmutableMap.of("preventUrlDownload", "false", "type", "STAC"))
                        .build()))
                .build());

        Job job = createJob(service);

        JobInputs jobInputs = JobInputs.builder()
                .jobId(job.getExtId())
                .inputs(ImmutableMap.of("stacInput",
                        JobInput.builder()
                                .id("stacInput")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("http://stac.url/path/to/catalog.json#featureId"))
                                .internalReference(new URL("http://internal.url/path/to/catalog.json"))
                                .build()))
                .build();

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, jobInputs, JobResourceRequirement.builder().storage(10240).gpus(5).build()),
                Collections.emptyList()).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .addInputs(JobParam.newBuilder()
                        .setType("STAC")
                        .setParamName("stacInput")
                        .addParamValue("http://internal.url/path/to/catalog.json")
                        .build())
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .setKind(Kind.WORKFLOW)
                .build());
    }

    @Test
    public void testBuildJobSpecBuilder_MapsParallelStacInputAsCatalogUrlWithFragment_WhenStacInputIsParallel() throws Exception {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("stacInput")
                        .title("stacInput")
                        .platformMetadata(ImmutableMap.of("preventUrlDownload", "false", "type", "STAC"))
                        .build()))
                .build());

        Job job = createJob(service);

        JobInputs jobInputs = JobInputs.builder()
                .jobId(job.getExtId())
                .inputs(ImmutableMap.of("stacInput",
                        JobInput.builder()
                                .id("stacInput")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("http://stac.url/path/to/catalog.json#featureId"))
                                .internalReference(new URL("http://internal.url/path/to/catalog.json"))
                                .parallelInput(true)
                                .build()))
                .build();

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, jobInputs, JobResourceRequirement.builder().storage(10240).gpus(5).build()),
                Collections.emptyList()).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .addInputs(JobParam.newBuilder()
                        .setType("STAC")
                        .setParamName("stacInput")
                        .addParamValue("http://internal.url/path/to/catalog.json#featureId")
                        .build())
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .setKind(Kind.WORKFLOW)
                .build());
    }

    @Test
    public void testBuildJobSpecBuilder_MapsInputWithSubsetting_WhenSubsettingIsSetInTheServiceDescriptor() {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(buildServiceInputsWithSubsetting())
                .build());

        Job job = createJob(service);

        JobInputs jobInputs = JobInputs.builder()
                .jobId(job.getExtId())
                .inputs(ImmutableMap.of(
                        "in",
                        JobInput.builder()
                                .id("in")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("inputValue"))
                                .parallelInput(false)
                                .build(),
                        "aoi",
                        JobInput.builder()
                                .id("aoi")
                                .type(JobInput.Type.OTHER)
                                .values(ImmutableList.of("aoiValue"))
                                .parallelInput(false)
                                .build()))
                .build();

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, jobInputs, JobResourceRequirement.builder().storage(10240).gpus(5).build()),
                Collections.emptyList()).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .addInputs(JobParam.newBuilder().setParamName("in").addParamValue("inputValue").setType("URL")
                        .setSubsetting(Subsetting.newBuilder().setAoi("aoiValue").setFormat("format").build()).build())
                .addInputs(JobParam.newBuilder().setParamName("aoi").addParamValue("aoiValue").setType("OTHER").build())
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .setKind(Kind.WORKFLOW)
                .build());
    }

    @Test
    public void testBuildJobSpecBuilder_MapsResourceRequestsAndSharedMemory_WhenTheyAreSet() {

        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setId(30L);
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("in").build()))
                .build());

        PlatformServiceResources.SharedMemory sharedMemory = new PlatformServiceResources.SharedMemory();
        sharedMemory.setSize("1Gi");
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setRam("10Gi");
        platformServiceResources.setCpus("0.5");
        platformServiceResources.setSharedMemory(sharedMemory);
        service.setRequiredResources(platformServiceResources);

        Job job = createJob(service);

        JobInputs jobInputs = JobInputs.builder()
                .jobId(job.getExtId())
                .inputs(ImmutableMap.of("in",
                        JobInput.builder()
                                .id("in")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("inputValue"))
                                .parallelInput(false)
                                .build()))
                .build();

        JobResourceRequirement jobResourceRequirement = JobResourceRequirement.builder()
                .storage(10240)
                .gpus(5)
                .limits(Limits.builder().ram("10240Mi").cpu("0.5").build())
                .requests(Requests.builder().ram("5000Mi").cpu("0.25").build())
                .build();

        JobSpec jobSpec = JobSpecMapper.buildJobSpecBuilder(
                buildJobSubmissionRequest(job, jobInputs, jobResourceRequirement),
                Collections.emptyList()).build();

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId("30")
                        .setName("serviceName")
                        .setDockerImageTag("dockerTag")
                        .setDescriptorType("")
                        .build())
                .setJob(buildExpectedRpcJob(job))
                .addInputs(JobParam.newBuilder().setParamName("in").addParamValue("inputValue").setType("URL").build())
                .setResourceRequest(ResourceRequest.newBuilder()
                        .setStorage(10240)
                        .setGpus("5")
                        .setRam("5000Mi")
                        .setCpus("0.25")
                        .setSharedMemory(SharedMemory.newBuilder().setSize("1Gi").build())
                        .setLimits(ResourceSpec.newBuilder().setRam("10240Mi").setCpu("0.5").build()))
                .setKind(Kind.WORKFLOW)
                .build());
    }

    private Job createJob(PlatformService service) {
        JobConfig jobConfig = new JobConfig(owner, service);
        Job job = new Job(jobConfig, "extId", owner);
        job.setId(12L);
        return job;
    }

    private com.cgi.eoss.platform.rpc.Job buildExpectedRpcJob(Job job) {
        return com.cgi.eoss.platform.rpc.Job.newBuilder()
                .setId(job.getExtId())
                .setIntJobId(String.valueOf(job.getId()))
                .setUserId(owner.getName())
                .setServiceId(job.getConfig().getService().getName())
                .build();
    }

    private JobSubmissionRequest buildJobSubmissionRequest(Job job, JobInputs jobInputs, JobResourceRequirement jobResourceRequirement) {
        return JobSubmissionRequest.builder()
                .job(job)
                .jobInputs(jobInputs)
                .jobResourceRequirement(jobResourceRequirement)
                .build();
    }

    private List<PlatformServiceDescriptor.Parameter> buildServiceInputsWithSubsetting() {
        List<PlatformServiceDescriptor.Parameter> serviceInputs = new ArrayList<>();
        serviceInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .id("in")
                .defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format("format").build())
                .build());
        serviceInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .id("aoi")
                .defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());
        return serviceInputs;
    }
}
