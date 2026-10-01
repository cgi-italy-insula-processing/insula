package com.cgi.eoss.platform.core.processing.server.orchestrator.service;


import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchRequest;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.core.queues.testutils.service.FaultInjectingBrokerPlugin;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchResponse;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.JmsException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class PlatformJobManagerIT {

    private static final int EXPECTED_JOB_PRIORITY = 1;

    private static final String JOB_EXECUTION = "platform-jobs";

    private static final String SERVICE_NAME = "Test Service";

    private static final String PARALLEL_INPUTS_KEY = "parallelProducts";

    private static final String INPUT_A = "sentinel2:///S2A_MSIL1C_20191210T101411_N0208_R022_T32TQM_20191210T104357.SAFE";

    private static final String INPUT_B = "sentinel2:///S2A_MSIL1C_20191107T100221_N0208_R122_T32TQM_20191107T103815.SAFE";

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private PlatformJobManager platformJobManager;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ServiceDataService svcService;

    @Autowired
    private JobDataService jobDataService;

    private User platformUser;


    @Before
    public void init() throws IOException {

        FaultInjectingBrokerPlugin.reset();

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        platformUser = userDataService.save(ProcessingCoreEntities.createUser().build());

        initQueue();
    }

    @After
    public void shutdown() {

        FaultInjectingBrokerPlugin.reset();

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testLaunchJob_SubmitsTheJobLaunchRequest_WhenServiceIsProcessor() {

        PlatformService svc = saveService(PlatformService.Type.PROCESSOR, "input", null);

        JobLaunchResponse jobLaunchResponse = platformJobManager.launchJob(JobLaunchRequest.builder()
            .jobId("1234")
            .userId(platformUser.getName())
            .serviceId(svc.getName())
            .inputList(createJobParameters(ImmutableMultimap.of("input", INPUT_A)))
            .build());

        assertThat(queueService.getQueueLength(JOB_EXECUTION)).isEqualTo(1);

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);

        Job job = jobs.get(0);
        assertThat(job.getExtId()).isEqualTo("1234");
        assertThat(job.isParent()).isFalse();
        assertThat(job.getParentJob()).isNull();
        assertThat(job.getStatus()).isEqualTo(Job.Status.PENDING);

        assertThat(job.getId()).isNotNull();
        assertThat(jobDataService.getSubJobIds(job)).isEmpty();
        assertThat(job.getPhase()).isEqualTo(JobStep.CREATED);
        assertThat(job.getConfig().getService().getId()).isNotNull();
        assertThat(job.getConfig().getService().getName()).isEqualTo(SERVICE_NAME);
        assertThat(job.getConfig().getInputs()).isEqualTo(ImmutableMultimap.of("input", INPUT_A));

        assertThat(jobLaunchResponse).isEqualTo(JobLaunchResponse.builder()
            .jobId("1234")
            .intJobId(String.valueOf(job.getId()))
            .userId(platformUser.getName())
            .serviceId(SERVICE_NAME)
            .build());

        Message message = queueService.receiveNoWait(JOB_EXECUTION);
        assertThat(message.getHeaders().get("jobId")).isEqualTo(String.valueOf(job.getId()));
        assertThat(message.getPriority()).isEqualTo(EXPECTED_JOB_PRIORITY);

        JobSpec jobSpec = (JobSpec) message.getPayload();
        assertThat(jobSpec).isEqualTo(
            JobSpec.newBuilder()
                .setKind(Kind.WORKFLOW)
                .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                    .setId("1234")
                    .setIntJobId(String.valueOf(job.getId()))
                    .setUserId(platformUser.getName())
                    .setServiceId(SERVICE_NAME)
                    .build())
                .setService(com.cgi.eoss.platform.rpc.Service.newBuilder()
                    .setId(String.valueOf(job.getConfig().getService().getId()))
                    .setName(SERVICE_NAME)
                    .setDockerImageTag("dockerTag")
                    .build())
                .setResourceRequest(
                    ResourceRequest.newBuilder()
                        .setLimits(
                            ResourceSpec.newBuilder()
                                .build()
                        )
                        .build()
                )
                .addInputs(JobParam.newBuilder()
                    .setParamName("input").addParamValue(INPUT_A).setType("URL").build())
                .build()
        );


        assertThat(queueService.getQueueLength(JOB_EXECUTION)).isZero();
    }

    @Test
    public void testLaunchJob_SubmitsTheJobLaunchRequest_WhenServiceIsParallelProcessor() {

        PlatformService svc = saveService(PlatformService.Type.PARALLEL_PROCESSOR, PARALLEL_INPUTS_KEY, PARALLEL_INPUTS_KEY);

        JobLaunchResponse jobLaunchResponse = platformJobManager.launchJob(JobLaunchRequest.builder()
            .jobId("parentJobId")
            .userId(platformUser.getName())
            .serviceId(svc.getName())
            .inputList(createJobParameters(ImmutableMultimap.of(PARALLEL_INPUTS_KEY, INPUT_A, PARALLEL_INPUTS_KEY, INPUT_B)))
            .build());

        assertThat(queueService.getQueueLength(JOB_EXECUTION)).isEqualTo(2);

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(3);

        List<Job> parentJobs = jobs.stream().filter(Job::isParent).collect(Collectors.toList());
        assertThat(parentJobs).hasSize(1);
        Job parentJob = parentJobs.get(0);
        assertThat(parentJob.isParent()).isTrue();
        assertThat(parentJob.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(parentJob.getPhase()).isEqualTo(JobStep.CREATED);
        assertThat(parentJob.getConfig().getService().getId()).isNotNull();
        assertThat(parentJob.getConfig().getService().getName()).isEqualTo(SERVICE_NAME);
        assertThat(parentJob.getConfig().getInputs()).isEqualTo(
            ImmutableMultimap.of(PARALLEL_INPUTS_KEY, INPUT_A, PARALLEL_INPUTS_KEY, INPUT_B)
        );

        List<Job> subJobs = jobs.stream().filter(job -> !job.isParent()).collect(Collectors.toList());
        assertThat(subJobs).hasSize(2);
        assertThat(subJobs).allSatisfy(subJob -> {
            assertThat(subJob.getParentJob().getId()).isEqualTo(parentJob.getId());
            assertThat(subJob.getStatus()).isEqualTo(Job.Status.PENDING);
            assertThat(subJob.getPhase()).isEqualTo(JobStep.CREATED);
            assertThat(subJob.isParent()).isFalse();
            assertThat(subJob.getConfig().getService().getId()).isNotNull();
            assertThat(subJob.getConfig().getService().getName()).isEqualTo(SERVICE_NAME);
            assertThat(jobDataService.getSubJobIds(subJob)).isEmpty();
        });
        assertThat(subJobs.get(0).getConfig().getInputs()).isEqualTo(ImmutableMultimap.of(PARALLEL_INPUTS_KEY, INPUT_A));
        assertThat(subJobs.get(1).getConfig().getInputs()).isEqualTo(ImmutableMultimap.of(PARALLEL_INPUTS_KEY, INPUT_B));

        assertThat(jobLaunchResponse).isEqualTo(JobLaunchResponse.builder()
            .jobId("parentJobId")
            .intJobId(String.valueOf(parentJob.getId()))
            .userId(platformUser.getName())
            .serviceId(SERVICE_NAME)
            .build());

        Message firstMessage = queueService.receiveNoWait(JOB_EXECUTION);
        assertThat(firstMessage.getPriority()).isEqualTo(EXPECTED_JOB_PRIORITY);
        JobSpec firstJobSpec = (JobSpec) firstMessage.getPayload();

        Message secondMessage = queueService.receiveNoWait(JOB_EXECUTION);
        assertThat(secondMessage.getPriority()).isEqualTo(EXPECTED_JOB_PRIORITY);
        JobSpec secondJobSpec = (JobSpec) secondMessage.getPayload();

        assertThat(ImmutableList.of(firstJobSpec, secondJobSpec))
            .extracting(jobSpec -> jobSpec.getJob().getIntJobId())
            .containsExactlyInAnyOrderElementsOf(
                ImmutableList.of(subJobs.get(0).getId().toString(),subJobs.get(1).getId().toString())
            );

        assertThat(firstJobSpec).isEqualTo(
            JobSpec.newBuilder()
                .setKind(Kind.WORKFLOW)
                .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                    .setId(subJobs.get(0).getExtId())
                    .setIntJobId(String.valueOf(subJobs.get(0).getId()))
                    .setUserId(platformUser.getName())
                    .setServiceId(SERVICE_NAME)
                    .build())
                .setService(com.cgi.eoss.platform.rpc.Service.newBuilder()
                    .setId(String.valueOf(subJobs.get(0).getConfig().getService().getId()))
                    .setName(SERVICE_NAME)
                    .setDockerImageTag("dockerTag")
                    .build())
                .setResourceRequest(
                    ResourceRequest.newBuilder()
                        .setLimits(
                            ResourceSpec.newBuilder()
                                .build()
                        )
                        .build()
                )
                .addInputs(JobParam.newBuilder()
                    .setParamName(PARALLEL_INPUTS_KEY).addParamValue(INPUT_A).setType("URL").build())
                .build()
        );

        assertThat(secondJobSpec).isEqualTo(
            JobSpec.newBuilder()
                .setKind(Kind.WORKFLOW)
                .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                    .setId(subJobs.get(1).getExtId())
                    .setIntJobId(String.valueOf(subJobs.get(1).getId()))
                    .setUserId(platformUser.getName())
                    .setServiceId(SERVICE_NAME)
                    .build())
                .setService(com.cgi.eoss.platform.rpc.Service.newBuilder()
                    .setId(String.valueOf(subJobs.get(1).getConfig().getService().getId()))
                    .setName(SERVICE_NAME)
                    .setDockerImageTag("dockerTag")
                    .build())
                .setResourceRequest(
                    ResourceRequest.newBuilder()
                        .setLimits(
                            ResourceSpec.newBuilder()
                                .build()
                        )
                        .build()
                )
                .addInputs(JobParam.newBuilder()
                    .setParamName(PARALLEL_INPUTS_KEY).addParamValue(INPUT_B).setType("URL").build())
                .build()
        );

        assertThat(queueService.getQueueLength(JOB_EXECUTION)).isZero();
    }

    @Test
    public void testLaunchJob_ThrowsJmsException_WhenJobSubmitterFails() {

        PlatformService svc = saveService(PlatformService.Type.PROCESSOR, "input", null);

        FaultInjectingBrokerPlugin.failSendsTo(JOB_EXECUTION);

        JobLaunchRequest jobLaunchRequest = JobLaunchRequest.builder()
            .jobId("1234")
            .userId(platformUser.getName())
            .serviceId(svc.getName())
            .inputList(createJobParameters(ImmutableMultimap.of("input", INPUT_A)))
            .build();

        assertThatThrownBy(() -> platformJobManager.launchJob(jobLaunchRequest))
            .isInstanceOf(JmsException.class)
            .hasMessageContaining("Fault injection: send rejected for " + JOB_EXECUTION);

        assertThat(queueService.getQueueLength(JOB_EXECUTION)).isZero();

        // The job has been created by the launcher core, but the submission transaction has been rolled back:
        // the job is left in its initial state and no message has been sent to the workers
        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);

        Job job = jobs.get(0);
        assertThat(job.getExtId()).isEqualTo("1234");
        assertThat(job.isParent()).isFalse();
        assertThat(job.getParentJob()).isNull();
        assertThat(job.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(job.getPhase()).isEqualTo(JobStep.CREATED);
        assertThat(job.getConfig().getService().getId()).isNotNull();
        assertThat(job.getConfig().getService().getName()).isEqualTo(SERVICE_NAME);
        assertThat(job.getConfig().getInputs()).isEqualTo(ImmutableMultimap.of("input", INPUT_A));
        assertThat(queueService.receiveNoWait(JOB_EXECUTION)).isNull();
    }

    private PlatformService saveService(PlatformService.Type type, String inputId, String parallelInputsKey) {
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createServiceParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createServiceParameter(inputId)));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService(SERVICE_NAME, platformUser, "dockerTag");
        svc.setType(type);
        svc.setServiceDescriptor(platformServiceDescriptor);

        return svcService.save(svc);
    }

    private static PlatformServiceDescriptor.Parameter createServiceParameter(String id) {
        return PlatformServiceDescriptor.Parameter.builder()
            .id(id)
            .title("the Title")
            .description("the Description")
            .minOccurs(1)
            .maxOccurs(1)
            .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
            .defaultAttrs(ImmutableMap.of("dataType", "string"))
            .supportedAttr(null)
            .platformMetadata(null)
            .build();
    }

    private List<JobLaunchRequest.Param> createJobParameters(Multimap<String, String> inputs) {
        return inputs.entries().stream().map(entry -> JobLaunchRequest.Param.builder()
                .name(entry.getKey())
                .value(Lists.newArrayList(entry.getValue()))
                .build())
            .collect(Collectors.toList());
    }

    private void initQueue() {
        queueService.sendObject(JOB_EXECUTION, "");
        queueService.receive(JOB_EXECUTION);

        assertThat(queueService.getQueueLength(JOB_EXECUTION)).isZero();
    }
}
