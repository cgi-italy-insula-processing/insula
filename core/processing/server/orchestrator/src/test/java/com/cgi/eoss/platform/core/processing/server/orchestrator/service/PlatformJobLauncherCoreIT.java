package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.exceptions.JobLaunchException;
import com.cgi.eoss.platform.core.processing.server.orchestrator.exceptions.ServiceExecutionException;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Limits;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Requests;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class PlatformJobLauncherCoreIT {

    private static final String INPUT_A = "sentinel2:///S2A_MSIL1C_20191210T101411_N0208_R022_T32TQM_20191210T104357.SAFE";

    private static final String INPUT_B = "sentinel2:///S2A_MSIL1C_20191107T100221_N0208_R122_T32TQM_20191107T103815.SAFE";

    @Autowired
    private PlatformJobLauncherCore platformJobLauncherCore;

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
    public void testSubmitJob_ThrowsPlatformEntityNotFoundException_WhenServiceCannotBeFound() {

        JobLaunchRequest request = JobLaunchRequest.builder()
                .jobId("1234")
                .userId(platformUser.getName())
                .serviceId("NonExistentService")
                .inputList(createParamList(ImmutableMultimap.of("input", INPUT_A)))
                .build();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(request))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Failed to load Service with ID: NonExistentService");

        assertThat(jobDataService.getAll()).isEmpty();
    }

    @Test
    public void testSubmitJob_ThrowsJobLaunchException_WhenServiceIsDisabled() {

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setServiceDescriptor(new PlatformServiceDescriptor());
        svc.setStatus(PlatformService.Status.DISABLED);
        svc = serviceDataService.save(svc);

        JobLaunchRequest request = JobLaunchRequest.builder()
                .jobId("41")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .inputList(createParamList(ImmutableMultimap.of("input", INPUT_A)))
                .build();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(request))
                .isInstanceOf(JobLaunchException.class)
                .hasMessage("Service disabled");

        assertThat(jobDataService.getAll()).isEmpty();
    }

    @Test
    public void testSubmitJob_ReturnsSingleRequestAndPersistsJob_WhenServiceTypeIsProcessor() {

        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter("input")));

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc.setRequiredResources(PlatformServiceResources.builder().storage("20").gpus("5").ram("600Mi").cpus("0.55").build());
        svc = serviceDataService.save(svc);

        List<JobSubmissionRequest> jobSubmissionRequests = platformJobLauncherCore.submitJob(JobLaunchRequest.builder()
                .jobId("1234")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .inputList(createParamList(ImmutableMultimap.of("input", INPUT_A)))
                .build());

        assertThat(jobSubmissionRequests).hasSize(1);

        JobSubmissionRequest jobSubmissionRequest = jobSubmissionRequests.get(0);
        assertThat(jobSubmissionRequest.getJobResourceRequirement()).isEqualTo(JobResourceRequirement.builder()
                .storage(20480)
                .gpus(5)
                .requests(Requests.builder().ram("600Mi").cpu("0.55").build())
                .limits(Limits.builder().ram("600Mi").cpu("0.55").build())
                .build());
        assertThat(jobSubmissionRequest.getJobInputs().getValuesMap().get("input")).containsExactly(INPUT_A);

        Job job = jobSubmissionRequest.getJob();
        assertThat(job.getId()).isNotNull();
        assertThat(job.getExtId()).isEqualTo("1234");
        assertThat(job.getOwner().getId()).isEqualTo(platformUser.getId());
        assertThat(job.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(job.isParent()).isFalse();
        assertThat(job.getParentJob()).isNull();
        assertThat(job.getConfig().getService().getName()).isEqualTo("Test Service");
        assertThat(job.getConfig().getService().getId()).isEqualTo(svc.getId());
        assertThat(job.getConfig().getOwner().getId()).isEqualTo(platformUser.getId());

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getId()).isEqualTo(job.getId());

        List<JobConfig> jobConfigs = jobConfigDataService.getAll();
        assertThat(jobConfigs).hasSize(1);
        assertThat(jobConfigs.get(0).getId()).isEqualTo(job.getConfig().getId());

        Multimap<String, String> inputs = job.getConfig().getInputs();
        assertThat(inputs.size()).isEqualTo(1);
        assertThat(inputs.get("input")).containsExactly(INPUT_A);
    }

    @Test
    public void testSubmitJob_AttachesJobToParent_WhenServiceTypeIsProcessorAndJobParentIsSet() {

        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter("input")));

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        JobConfig parentJobConfig = new JobConfig();
        parentJobConfig.setService(svc);
        parentJobConfig.setOwner(platformUser);
        parentJobConfig.setInputs(ImmutableMultimap.of("input", INPUT_A));
        parentJobConfig = jobConfigDataService.save(parentJobConfig);
        Job parentJob = new Job();
        parentJob.setExtId("parentJobExtId");
        parentJob.setParent(true);
        parentJob.setConfig(parentJobConfig);
        parentJob.setOwner(platformUser);
        parentJob = jobDataService.save(parentJob);

        List<JobSubmissionRequest> jobSubmissionRequests = platformJobLauncherCore.submitJob(JobLaunchRequest.builder()
                .jobId("childJobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .jobParent(String.valueOf(parentJob.getId()))
                .inputList(createParamList(ImmutableMultimap.of("input", INPUT_A)))
                .build());

        assertThat(jobSubmissionRequests).hasSize(1);

        Job childJob = jobSubmissionRequests.get(0).getJob();
        assertThat(childJob.getExtId()).isEqualTo("childJobId");
        assertThat(childJob.isParent()).isFalse();
        assertThat(childJob.getStatus()).isEqualTo(Job.Status.CREATED);

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(2);
        assertThat(jobs).filteredOn(Job::isParent).hasSize(1);

        Optional<Job> parentJobOpt = jobDataService.getById(parentJob.getId());
        assertThat(parentJobOpt).isNotEmpty();
        Job extractedParentJob = parentJobOpt.get();
        assertThat(extractedParentJob.getExtId()).isEqualTo("parentJobExtId");
        assertThat(childJob.getParentJob().getId()).isEqualTo(extractedParentJob.getId());

        assertThat(jobs).extracting(Job::getId)
                .containsExactlyInAnyOrder(extractedParentJob.getId(), childJob.getId());
    }

    @Test
    public void testSubmitJob_ThrowsPlatformEntityNotFoundException_WhenServiceTypeIsProcessorAndReferencedParentJobDoesNotExist() {

        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter("input")));

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        JobLaunchRequest request = JobLaunchRequest.builder()
                .jobId("childJobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .jobParent("424242")
                .inputList(createParamList(ImmutableMultimap.of("input", INPUT_A)))
                .build();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(request))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Failed to load parent Job with ID: 424242");

        assertThat(jobDataService.getAll()).isEmpty();
    }

    @Test
    public void testSubmitJob_ThrowsServiceExecutionExceptionAndCreatesJobWithStatusError_WhenServiceTypeIsProcessorAndJobValidationFails() {

        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("in")
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .defaultAttrs(ImmutableMap.of("dataType", "integer"))
                        .minOccurs(1)
                        .maxOccurs(1)
                        .build()));

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        JobLaunchRequest request = JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .inputList(createParamList(ImmutableMultimap.of("in", "notAnInteger")))
                .build();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(request))
                .isInstanceOf(ServiceExecutionException.class)
                .hasMessage("Value does not match type for input(s): in");

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);

        Job job = jobs.get(0);
        assertThat(job.isParent()).isFalse();
        assertThat(job.getParentJob()).isNull();
        assertThat(job.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(job.getConfig().getInputs().get("in")).containsExactly("notAnInteger");
    }

    @Test
    public void testSubmitJob_ReturnsSubJobRequestsAndCreatesFreshParent_WhenServiceTypeIsParallelProcessorAndJobParentIsNotSet() {

        String parallelInputsKey = "parallelProducts";
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter(parallelInputsKey)));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PARALLEL_PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        Multimap<String, String> jobInputs = ImmutableMultimap.of(
                parallelInputsKey, INPUT_A,
                parallelInputsKey, INPUT_B);

        List<JobSubmissionRequest> jobSubmissionRequests = platformJobLauncherCore.submitJob(JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .inputList(createParamList(jobInputs))
                .build());

        assertThat(jobSubmissionRequests).hasSize(2);

        Job subJob1 = jobSubmissionRequests.get(0).getJob();
        assertThat(subJob1.isParent()).isFalse();
        assertThat(subJob1.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(subJob1.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        Job subJob2 = jobSubmissionRequests.get(1).getJob();
        assertThat(subJob2.isParent()).isFalse();
        assertThat(subJob2.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(subJob2.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        assertThat(jobSubmissionRequests)
                .extracting(request -> request.getJob().getConfig().getInputs().get(parallelInputsKey).iterator().next())
                .containsExactlyInAnyOrder(INPUT_A, INPUT_B);

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(3);
        assertThat(jobs).filteredOn(Job::isParent).hasSize(1);

        List<Job> parentJobs = jobDataService.findByExternalId(ImmutableList.of("jobId"));
        assertThat(parentJobs).hasSize(1);
        Job parentJob = parentJobs.get(0);
        assertThat(parentJob.getExtId()).isEqualTo("jobId");
        assertThat(parentJob.getParentJob()).isNull();
        assertThat(parentJob.isParent()).isTrue();
        assertThat(parentJob.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(parentJob.getConfig().getInputs().get(parallelInputsKey)).hasSize(2)
                .containsExactlyInAnyOrder(INPUT_A, INPUT_B);

        assertThat(subJob1.getParentJob().getId()).isEqualTo(parentJob.getId());
        assertThat(subJob2.getParentJob().getId()).isEqualTo(parentJob.getId());
    }

    @Test
    public void testSubmitJob_AttachesToExistingParent_WhenServiceTypeIsParallelProcessorAndJobParentIsSet() {

        String parallelInputsKey = "parallelProducts";
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter(parallelInputsKey)));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PARALLEL_PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        Multimap<String, String> jobInputs = ImmutableMultimap.of(
                parallelInputsKey, INPUT_A,
                parallelInputsKey, INPUT_B);

        JobConfig parentJobConfig = new JobConfig();
        parentJobConfig.setService(svc);
        parentJobConfig.setOwner(platformUser);
        parentJobConfig.setInputs(jobInputs);
        parentJobConfig = jobConfigDataService.save(parentJobConfig);
        Job parentJob = new Job();
        parentJob.setExtId("parentJobExtId");
        parentJob.setParent(true);
        parentJob.setConfig(parentJobConfig);
        parentJob.setOwner(platformUser);
        parentJob = jobDataService.save(parentJob);

        List<JobSubmissionRequest> jobSubmissionRequests = platformJobLauncherCore.submitJob(JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .jobParent(String.valueOf(parentJob.getId()))
                .inputList(createParamList(jobInputs))
                .build());

        assertThat(jobSubmissionRequests).hasSize(2);

        Job subJob1 = jobSubmissionRequests.get(0).getJob();
        assertThat(subJob1.isParent()).isFalse();
        assertThat(subJob1.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(subJob1.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        Job subJob2 = jobSubmissionRequests.get(1).getJob();
        assertThat(subJob2.isParent()).isFalse();
        assertThat(subJob2.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(subJob2.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        assertThat(jobSubmissionRequests)
                .extracting(request -> request.getJob().getConfig().getInputs().get(parallelInputsKey).iterator().next())
                .containsExactlyInAnyOrder(INPUT_A, INPUT_B);

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(3);
        assertThat(jobs).filteredOn(Job::isParent).hasSize(1);

        Optional<Job> parentJobOpt = jobDataService.getById(parentJob.getId());
        assertThat(parentJobOpt).isNotEmpty();
        Job extractedParentJob = parentJobOpt.get();
        assertThat(extractedParentJob.getExtId()).isEqualTo("parentJobExtId");

        assertThat(subJob1.getParentJob().getId()).isEqualTo(extractedParentJob.getId());
        assertThat(subJob2.getParentJob().getId()).isEqualTo(extractedParentJob.getId());

        assertThat(jobs).extracting(Job::getId)
                .containsExactlyInAnyOrder(extractedParentJob.getId(), subJob1.getId(), subJob2.getId());
    }

    @Test
    public void testSubmitJob_ThrowsPlatformEntityNotFoundException_WhenServiceTypeIsParallelProcessorAndReferencedParentJobDoesNotExist() {

        String parallelInputsKey = "parallelProducts";
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter(parallelInputsKey)));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PARALLEL_PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        Multimap<String, String> jobInputs = ImmutableMultimap.of(
                parallelInputsKey, INPUT_A,
                parallelInputsKey, INPUT_B);

        JobLaunchRequest request = JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .jobParent("424242")
                .inputList(createParamList(jobInputs))
                .build();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(request))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Failed to load parent Job with ID: 424242");

        assertThat(jobDataService.getAll()).isEmpty();
    }

    @Test
    public void testSubmitJob_ThrowsServiceExecutionExceptionAndCreatesParentAndChildJobsWithStatusError_WhenServiceTypeIsParallelProcessorAndJobValidationFailsForAllSubJobs() {

        String parallelInputsKey = "parallelProducts";
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(
                PlatformServiceDescriptor.Parameter.builder()
                        .id(parallelInputsKey)
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .defaultAttrs(ImmutableMap.of("dataType", "integer"))
                        .minOccurs(1)
                        .maxOccurs(1)
                        .build()));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PARALLEL_PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        Multimap<String, String> jobInputs = ImmutableMultimap.of(
                parallelInputsKey, "notAnInteger1",
                parallelInputsKey, "notAnInteger2");

        String serviceId = svc.getName();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(serviceId)
                .inputList(createParamList(jobInputs))
                .build()))
                .isInstanceOf(ServiceExecutionException.class)
                .hasMessageContaining("No valid sub jobs exist for parent job with ID:");

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(3);
        assertThat(jobs).filteredOn(Job::isParent).hasSize(1);

        List<Job> parentJobs = jobDataService.findByExternalId(ImmutableList.of("jobId"));
        assertThat(parentJobs).hasSize(1);
        Job parentJob = parentJobs.get(0);
        assertThat(parentJob.getParentJob()).isNull();
        assertThat(parentJob.isParent()).isTrue();
        assertThat(parentJob.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(parentJob.getConfig().getInputs().get(parallelInputsKey))
                .hasSize(2)
                .containsExactlyInAnyOrder("notAnInteger1", "notAnInteger2");

        List<Job> subJobs = jobDataService.findByIds(jobDataService.getSubJobIds(parentJob));
        assertThat(subJobs).hasSize(2);

        Job subJob1 = subJobs.get(0);
        assertThat(subJob1.isParent()).isFalse();
        assertThat(subJob1.getParentJob().getId()).isEqualTo(parentJob.getId());
        assertThat(subJob1.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(subJob1.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        Job subJob2 = subJobs.get(1);
        assertThat(subJob2.isParent()).isFalse();
        assertThat(subJob2.getParentJob().getId()).isEqualTo(parentJob.getId());
        assertThat(subJob2.getStatus()).isEqualTo(Job.Status.ERROR);
        assertThat(subJob2.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        assertThat(subJobs)
                .extracting(subJob -> subJob.getConfig().getInputs().get(parallelInputsKey).iterator().next())
                .containsExactlyInAnyOrder("notAnInteger1", "notAnInteger2");
    }

    @Test
    public void testSubmitJob_InjectsSystematicParameterFromRequestAndCreatesSubJobs_WhenServiceTypeIsParallelProcessorAndParentIsSystematic() {

        String parallelInputsKey = "parallelProducts";
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter(parallelInputsKey)));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PARALLEL_PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        JobConfig parentJobConfig = new JobConfig();
        parentJobConfig.setService(svc);
        parentJobConfig.setOwner(platformUser);
        parentJobConfig.setInputs(ImmutableMultimap.of());
        parentJobConfig.setSystematicParameter(parallelInputsKey);
        parentJobConfig = jobConfigDataService.save(parentJobConfig);
        Job parentJob = new Job();
        parentJob.setExtId("parentJobExtId");
        parentJob.setParent(true);
        parentJob.setConfig(parentJobConfig);
        parentJob.setOwner(platformUser);
        parentJob = jobDataService.save(parentJob);

        List<JobSubmissionRequest> jobSubmissionRequests = platformJobLauncherCore.submitJob(JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .jobParent(String.valueOf(parentJob.getId()))
                .inputList(createParamList(ImmutableMultimap.of(
                        parallelInputsKey, INPUT_A,
                        parallelInputsKey, INPUT_B)))
                .build());

        assertThat(jobSubmissionRequests).hasSize(2);

        Job subJob1 = jobSubmissionRequests.get(0).getJob();
        assertThat(subJob1.isParent()).isFalse();
        assertThat(subJob1.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(subJob1.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        Job subJob2 = jobSubmissionRequests.get(1).getJob();
        assertThat(subJob2.isParent()).isFalse();
        assertThat(subJob2.getStatus()).isEqualTo(Job.Status.CREATED);
        assertThat(subJob2.getConfig().getInputs().get(parallelInputsKey)).hasSize(1);

        assertThat(jobSubmissionRequests)
                .extracting(request -> request.getJob().getConfig().getInputs().get(parallelInputsKey).iterator().next())
                .containsExactlyInAnyOrder(INPUT_A, INPUT_B);

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(3);
        assertThat(jobs).filteredOn(Job::isParent).hasSize(1);

        Job extractedParentJob = jobDataService.getById(parentJob.getId()).orElseThrow(AssertionError::new);
        assertThat(extractedParentJob.getExtId()).isEqualTo("parentJobExtId");
        assertThat(subJob1.getParentJob().getId()).isEqualTo(extractedParentJob.getId());
        assertThat(subJob2.getParentJob().getId()).isEqualTo(extractedParentJob.getId());

        assertThat(extractedParentJob.getConfig().getInputs().keySet()).doesNotContain(parallelInputsKey);
    }

    @Test
    public void testSubmitJob_ThrowsServiceExecutionException_WhenServiceTypeIsParallelProcessorAndParentIsSystematicButRequestHasNoSystematicParameter() {

        String parallelInputsKey = "parallelProducts";
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(ImmutableList.of(createParameter("output_id_1")));
        platformServiceDescriptor.setDataInputs(ImmutableList.of(createParameter(parallelInputsKey)));
        platformServiceDescriptor.setParallelInputsKey(parallelInputsKey);

        PlatformService svc = new PlatformService("Test Service", platformUser, "dockerTag");
        svc.setType(PlatformService.Type.PARALLEL_PROCESSOR);
        svc.setServiceDescriptor(platformServiceDescriptor);
        svc = serviceDataService.save(svc);

        JobConfig parentJobConfig = new JobConfig();
        parentJobConfig.setService(svc);
        parentJobConfig.setOwner(platformUser);
        parentJobConfig.setInputs(ImmutableMultimap.of());
        parentJobConfig.setSystematicParameter(parallelInputsKey);
        parentJobConfig = jobConfigDataService.save(parentJobConfig);
        Job parentJob = new Job();
        parentJob.setExtId("parentJobExtId");
        parentJob.setParent(true);
        parentJob.setConfig(parentJobConfig);
        parentJob.setOwner(platformUser);
        parentJob = jobDataService.save(parentJob);

        JobLaunchRequest request = JobLaunchRequest.builder()
                .jobId("jobId")
                .userId(platformUser.getName())
                .serviceId(svc.getName())
                .jobParent(String.valueOf(parentJob.getId()))
                .inputList(createParamList(ImmutableMultimap.of("someOtherInput", INPUT_A)))
                .build();

        assertThatThrownBy(() -> platformJobLauncherCore.submitJob(request))
                .isInstanceOf(ServiceExecutionException.class)
                .hasMessage("Systematic parameter '" + parallelInputsKey + "' not found in launch request inputs");

        assertThat(jobDataService.getAll()).containsExactly(parentJob);
    }

    private static PlatformServiceDescriptor.Parameter createParameter(String id) {
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

    private List<JobLaunchRequest.Param> createParamList(Multimap<String, String> inputs) {
        return inputs.entries().stream().map(entry -> JobLaunchRequest.Param.builder()
                        .name(entry.getKey())
                        .value(Lists.newArrayList(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

}