package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserMountDataService;

import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.Service;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class CoreProcessorJobSpecProducerIT {

    @Autowired
    private UserMountResolver userMountResolver;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Autowired
    private JobDataService jobDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private UserMountDataService userMountDataService;

    private User platformUser;

    private CoreProcessorJobSpecProducer coreProcessorJobSpecProducer;

    @Before
    public void init() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        platformUser = userDataService.save(ProcessingCoreEntities.createUser().build());
        coreProcessorJobSpecProducer = new CoreProcessorJobSpecProducer(jobDataService, userMountResolver);
    }

    @After
    public void shutdown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testSubmitJob_ReturnsWorkflowJobSpecAndPersistsJobAsPending_WhenServiceTypeIsProcessor() {

        PlatformService service = new PlatformService("Test Service", platformUser, "dockerTag");
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("in").build()))
                .build());
        service = serviceDataService.save(service);

        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(platformUser, service));
        Job job = jobDataService.save(new Job(jobConfig, "1234", platformUser));
        assertThat(job.getStatus()).isEqualTo(Job.Status.CREATED);

        JobSpec jobSpec = coreProcessorJobSpecProducer.produceJobSpec(JobSubmissionRequest.builder()
                .job(job)
                .jobInputs(JobInputs.builder().jobId(job.getExtId()).inputs(ImmutableMap.of("in",
                        JobInput.builder()
                                .id("in")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("inputValue"))
                                .parallelInput(false)
                                .build())).build())
                .jobResourceRequirement(JobResourceRequirement.builder().storage(10240).gpus(5).build())
                .build());

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId(String.valueOf(service.getId()))
                        .setName("Test Service")
                        .setDockerImageTag("dockerTag")
                        .build())
                .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId("1234")
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId(platformUser.getName())
                        .setServiceId("Test Service")
                        .build())
                .addInputs(JobParam.newBuilder().setParamName("in").addParamValue("inputValue").setType("URL").build())
                .setKind(Kind.WORKFLOW)
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .build());

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);

        Job persistedJob = jobs.get(0);
        assertThat(persistedJob.getId()).isEqualTo(job.getId());
        assertThat(persistedJob.getExtId()).isEqualTo("1234");
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.PENDING);
        assertThat(persistedJob.getOwner()).isEqualTo(platformUser);
        assertThat(persistedJob.getConfig().getId()).isEqualTo(jobConfig.getId());
        assertThat(persistedJob.getConfig().getService().getId()).isEqualTo(service.getId());
    }

    @Test
    public void testSubmitJob_ReturnsJobSpecWithResolvedUserMounts_WhenServiceHasAdditionalMounts() {

        UserMount userMount = new UserMount("bps-user-mount", "mountPath", UserMount.MountType.RO);
        userMount.setOwner(platformUser);
        userMount = userMountDataService.save(userMount);

        PlatformService service = new PlatformService("Test Service", platformUser, "dockerTag");
        service.setType(PlatformService.Type.PROCESSOR);
        service.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("in").build()))
                .build());
        Map<Long, String> additionalMounts = new HashMap<>();
        additionalMounts.put(userMount.getId(), "/home/workdir/mountpath");
        service.setAdditionalMounts(additionalMounts);
        service = serviceDataService.save(service);

        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(platformUser, service));
        Job job = jobDataService.save(new Job(jobConfig, "1234", platformUser));

        JobSpec jobSpec = coreProcessorJobSpecProducer.produceJobSpec(JobSubmissionRequest.builder()
                .job(job)
                .jobInputs(JobInputs.builder().jobId(job.getExtId()).inputs(ImmutableMap.of("in",
                        JobInput.builder()
                                .id("in")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("inputValue"))
                                .parallelInput(false)
                                .build())).build())
                .jobResourceRequirement(JobResourceRequirement.builder().storage(10240).gpus(5).build())
                .build());

        assertThat(jobSpec).isEqualTo(JobSpec.newBuilder()
                .setService(Service.newBuilder()
                        .setId(String.valueOf(service.getId()))
                        .setName("Test Service")
                        .setDockerImageTag("dockerTag")
                        .build())
                .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                        .setId("1234")
                        .setIntJobId(String.valueOf(job.getId()))
                        .setUserId(platformUser.getName())
                        .setServiceId("Test Service")
                        .build())
                .addInputs(JobParam.newBuilder().setParamName("in").addParamValue("inputValue").setType("URL").build())
                .addAllUserMount(ImmutableList.of(com.cgi.eoss.platform.rpc.UserMount.newBuilder()
                        .setName("bps-user-mount")
                        .setMountPath("mountPath")
                        .setType("ro")
                        .setTargetPath("/home/workdir/mountpath")
                        .build()))
                .setKind(Kind.WORKFLOW)
                .setResourceRequest(ResourceRequest.newBuilder().setStorage(10240).setGpus("5").build())
                .build());

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);

        Job persistedJob = jobs.get(0);
        assertThat(persistedJob.getId()).isEqualTo(job.getId());
        assertThat(persistedJob.getExtId()).isEqualTo("1234");
        assertThat(persistedJob.getStatus()).isEqualTo(Job.Status.PENDING);
        assertThat(persistedJob.getOwner()).isEqualTo(platformUser);
        assertThat(persistedJob.getConfig().getId()).isEqualTo(jobConfig.getId());
        assertThat(persistedJob.getConfig().getService().getId()).isEqualTo(service.getId());
    }
}