package com.cgi.eoss.platform.core.processing.server.persistence.service;

import static com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities.createJob;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.JobDao;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {PersistenceCoreConfig.class})
@TestPropertySource("classpath:test-persistence-core.properties")
public class JobDataServiceIT {

    @Autowired
    private JobDataService jobDataService;
    @Autowired
    private JobConfigDataService jobConfigService;
    @Autowired
    private UserDataService userService;
    @Autowired
    private ServiceDataService svcService;

    @Autowired
    private JobDao jobDao;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    private User owner;

    @Before
    public void init() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        owner = userService.save(new User("owner-uid"));
    }

    @After
    public void shutdown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void test() throws Exception {
        User owner = new User("owner-uid");
        User owner2 = new User("owner-uid2");
        userService.save(ImmutableSet.of(owner, owner2));

        PlatformService svc = new PlatformService();
        svc.setName("Test Service");
        svc.setOwner(owner);
        svc.setDockerTag("dockerTag");
        PlatformService svc2 = new PlatformService();
        svc2.setName("Test Service2");
        svc2.setOwner(owner);
        svc2.setDockerTag("dockerTag");
        svcService.save(ImmutableSet.of(svc, svc2));

        Multimap<String, String> job1Inputs = ImmutableMultimap.of(
                "input1", "foo",
                "input2", "bar1",
                "input2", "bar2",
                "input3", "http://baz/?q=x,y&z={}");
        JobConfig jobConfig = new JobConfig(owner, svc);
        jobConfig.setInputs(job1Inputs);
        JobConfig jobConfig2 = new JobConfig(owner, svc2);
        jobConfigService.save(ImmutableList.of(jobConfig, jobConfig2));

        Job job1 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        job1.setStatus(Status.RUNNING);
        Job job2 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        Job job3 = new Job(jobConfig, UUID.randomUUID().toString(), owner2);
        Job job4 = new Job(jobConfig, UUID.randomUUID().toString(), owner2);
        jobDataService.save(ImmutableList.of(job1, job2, job3, job4));

        assertThat(jobDataService.getAll()).containsExactlyInAnyOrder(job1, job2, job3, job4);
        assertThat(jobDataService.countByOwnerAndStatusIn(owner, ImmutableList.of(Status.RUNNING))).isEqualTo(1);
    }

    @Test
    public void testStartTimeIsPropagatedToTheParentJob_WhenChildJobIsSaved_OnlyIfParentJobStartTimeIsNull() throws Exception {
        Long parentJobId;
        Long childJobId1;
        Long childJobId2;
        // Initialize the context: one parent job with two child jobs
        {
            User owner = userService.save(new User("owner-uid"));
            PlatformService svc = svcService.save(new PlatformService("Test Service", owner, "dockerTag"));

            JobConfig jobConfig = jobConfigService.save(new JobConfig(owner, svc));

            Job parentJob = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));
            assertThat(parentJob.getId()).isNotNull();
            parentJobId = parentJob.getId();

            Job childJob1 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
            childJob1.setParentJob(parentJob);
            childJobId1 = jobDataService.save(childJob1).getId();

            Job childJob2 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
            childJob2.setParentJob(parentJob);
            childJobId2 = jobDataService.save(childJob2).getId();

            Optional<Job> job = jobDataService.refreshFull(parentJobId);
            assertThat(getSubJobs(job.get())).hasSize(2);
        }

        LocalDateTime startTimeChildJob1 = LocalDateTime.now();

        {
            // Retrieve the first child job
            Job childJob1 = jobDataService.getById(childJobId1).get();
            assertThat(childJob1.getParentJob().getId()).isEqualTo(parentJobId);

            // Update the start time of the first child job
            childJob1.setStartTime(startTimeChildJob1);
            childJob1 = jobDataService.save(childJob1);

            // Ensure that the start date of the first child job was propagated to the parent (both in memory and on the DB)
            assertThat(childJob1.getStartTime()).isEqualTo(startTimeChildJob1);
            assertThat(childJob1.getParentJob().getStartTime()).isEqualTo(startTimeChildJob1);

            assertThat(jobDataService.getById(childJobId1).get().getStartTime()).isEqualTo(startTimeChildJob1);
            assertThat(jobDataService.getById(parentJobId).get().getStartTime()).isEqualTo(startTimeChildJob1);
        }

        // Check that the start date of the parent job is NOT updated to the value of the second child job
        {

            // Retrieve the second child job
            Job childJob2 = jobDataService.getById(childJobId2).get();
            assertThat(childJob2.getParentJob().getId()).isEqualTo(parentJobId);
            assertThat(childJob2.getParentJob().getStartTime()).isEqualTo(startTimeChildJob1);

            // Update the start time of the second child job
            LocalDateTime startTimeChildJob2 = startTimeChildJob1.plusSeconds(1);
            childJob2.setStartTime(startTimeChildJob2);
            childJob2 = jobDataService.save(childJob2);

            // Ensure that the start date of the second child job was NOT propagated to the parent
            assertThat(childJob2.getStartTime()).isEqualTo(startTimeChildJob2);
            assertThat(childJob2.getParentJob().getStartTime()).isEqualTo(startTimeChildJob1);

            assertThat(jobDataService.getById(childJobId2).get().getStartTime()).isEqualTo(startTimeChildJob2);
            assertThat(jobDataService.getById(parentJobId).get().getStartTime()).isEqualTo(startTimeChildJob1);
        }
    }

    @Test
    public void testStartTimeIsPropagatedToTheParentJob_WhenChildrenJobAreSaved_OnlyIfParentJobStartTimeIsNull() {
        Long parentJobId;
        Long childJobId1;
        Long childJobId2;
        // Initialize the context: one parent job with two child jobs
        {
            User owner = userService.save(new User("owner-uid"));
            PlatformService svc = svcService.save(new PlatformService("Test Service", owner, "dockerTag"));

            JobConfig jobConfig = jobConfigService.save(new JobConfig(owner, svc));

            Job parentJob = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));
            assertThat(parentJob.getId()).isNotNull();
            parentJobId = parentJob.getId();

            Job childJob1 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
            childJob1.setParentJob(parentJob);
            childJobId1 = jobDataService.save(childJob1).getId();

            Job childJob2 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
            childJob2.setParentJob(parentJob);
            childJobId2 = jobDataService.save(childJob2).getId();

            assertThat(getSubJobs(parentJob)).hasSize(2);
        }

        LocalDateTime startTimeChildJob1 = LocalDateTime.now();
        LocalDateTime startTimeChildJob2 = startTimeChildJob1.plusSeconds(1);

        {
            // Retrieve the children jobs
            Job childJob1 = jobDataService.getById(childJobId1).get();
            Job childJob2 = jobDataService.getById(childJobId2).get();

            // Update the start time of the first child job
            childJob1.setStartTime(startTimeChildJob1);

            // Update the start time of the second child job
            childJob2.setStartTime(startTimeChildJob2);

            // Update children jobs at the same time
            ArrayList<Job> children = new ArrayList<>(jobDataService.save(Arrays.asList(childJob1, childJob2)));
            assertThat(children.get(0).getId()).isEqualTo(childJobId1);
            assertThat(children.get(0).getParentJob().getStartTime()).isEqualTo(startTimeChildJob1);
            assertThat(children.get(0).getStartTime()).isEqualTo(startTimeChildJob1);

            assertThat(children.get(1).getId()).isEqualTo(childJobId2);
            assertThat(children.get(1).getParentJob().getStartTime()).isEqualTo(startTimeChildJob1);
            assertThat(children.get(1).getStartTime()).isEqualTo(startTimeChildJob2);
        }

        // Ensure that the start date of the child job was propagated to the parent
        assertThat(jobDataService.getById(childJobId1).get().getStartTime()).isEqualTo(startTimeChildJob1);
        assertThat(jobDataService.getById(parentJobId).get().getStartTime()).isEqualTo(startTimeChildJob1);

        // Ensure that the start date of the second child job was NOT propagated to the parent
        assertThat(jobDataService.getById(childJobId2).get().getStartTime()).isEqualTo(startTimeChildJob2);

    }

    @Test
    public void testSave_CreatesJobWithCreatedAndLastUpdateTimestampsSetToTheCreationTime_WhenJobDoesNotExistOnTheDb() {
        JobConfig jobConfig = createJobConfig();

        Job job = new Job(jobConfig, UUID.randomUUID().toString(), owner);

        assertThat(job.getCreated()).isNull();
        assertThat(job.getLastUpdated()).isNull();

        OffsetDateTime beforeCreate = OffsetDateTime.now();
        job = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));
        OffsetDateTime afterCreate = OffsetDateTime.now();

        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);
        Job retrievedJob = jobs.get(0);

        assertThat(job.getId()).isNotNull();
        assertThat(retrievedJob.getId()).isEqualTo(job.getId());
        assertThat(retrievedJob.getLastUpdated()).isBetween(beforeCreate, afterCreate);
        assertThat(retrievedJob.getCreated()).isBetween(beforeCreate, afterCreate);
    }

    @Test
    public void testSave_UpdatesJobLastUpdateTimestampWithCurrentTimestamp_WhenJobAlreadyExistsOnTheDb() {
        JobConfig jobConfig = createJobConfig();

        OffsetDateTime beforeCreate = OffsetDateTime.now();
        Job job = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));
        OffsetDateTime afterCreate = OffsetDateTime.now();
        List<Job> jobs = jobDataService.getAll();
        assertThat(jobs).hasSize(1);
        Job retrievedJob = jobs.get(0);
        assertThat(job.getId()).isNotNull();
        assertThat(retrievedJob.getId()).isEqualTo(job.getId());

        assertThat(retrievedJob.getLastUpdated()).isBetween(beforeCreate, afterCreate);
        assertThat(retrievedJob.getStatus()).isEqualTo(Status.CREATED);

        job.setStatus(Status.RUNNING);
        OffsetDateTime beforeUpdate = OffsetDateTime.now();
        job = jobDataService.save(job);
        OffsetDateTime afterUpdate = OffsetDateTime.now();
        List<Job> jobsAfterUpdate = jobDataService.getAll();
        assertThat(jobsAfterUpdate).hasSize(1);
        Job retrievedJobAfterUpdate = jobsAfterUpdate.get(0);
        assertThat(job.getId()).isNotNull();
        assertThat(retrievedJobAfterUpdate.getId()).isEqualTo(job.getId());

        assertThat(retrievedJobAfterUpdate.getLastUpdated()).isBetween(beforeUpdate, afterUpdate);
        assertThat(retrievedJobAfterUpdate.getStatus()).isEqualTo(Status.RUNNING);
    }

    @Test
    public void testFindByExternalId_ReturnsListOfAllMatchingJobs_WhenJobsExist() {

        JobConfig jobConfig = createJobConfig();

        String externalId1 = UUID.randomUUID().toString();
        Job job1 = jobDataService.save(new Job(jobConfig, externalId1, owner));

        String externalId2 = UUID.randomUUID().toString();
        Job job2 = jobDataService.save(new Job(jobConfig, externalId2, owner));

        String externalId3 = UUID.randomUUID().toString();
        Job job3 = jobDataService.save(new Job(jobConfig, externalId3, owner));

        assertThat(
                jobDataService.findByExternalId(Arrays.asList(externalId1, externalId2))
        ).containsExactlyInAnyOrder(job1, job2);
        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(job1, job2, job3);

    }

    @Test
    public void testFindByExternalId_ReturnsEmptyList_WhenJobsDoNotExist() {

        assertThat(jobDataService.findByExternalId(Arrays.asList("non-existing-external-id_1", "non-existing-external-id_2"))).isEmpty();
    }

    @Test
    public void testFindByIds_ReturnsListOfAllMatchingJobsOrderedById_WhenJobExists() {

        JobConfig jobConfig = createJobConfig();

        Job job1 = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));
        Job job2 = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));
        Job job3 = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));

        assertThat(
                jobDataService.findByIds(Arrays.asList(job1.getId(), job3.getId()))
        ).containsExactly(job1, job3);
        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(job1, job2, job3);

    }

    @Test
    public void testFindByIds_ReturnsEmptyList_WhenJobsDoNotExist() {
        assertThat(jobDataService.findByIds(Arrays.asList(999L, 1000L))).isEmpty();
    }

    @Test
    public void testGetSubJobIds_ReturnsTheOrderedListOfIdsOfTheSubJobsOfTheProvidedParentJob_WhenSubJobsExist() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));

        Job subJob1 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        subJob1.setParentJob(parentJob);
        subJob1 = jobDataService.save(subJob1);

        Job subJob2 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        subJob2.setParentJob(parentJob);
        subJob2 = jobDataService.save(subJob2);

        List<Long> subJobIds = jobDataService.getSubJobIds(parentJob);

        assertThat(subJobIds).containsExactly(subJob1.getId(), subJob2.getId());
    }

    @Test
    public void testGetSubJobIds_ReturnsEmptyList_WhenJobsDoNotExist() {

        Job job = new Job();
        job.setId(42L);

        assertThat(jobDataService.getSubJobIds(job)).isEmpty();
    }

    @Test
    public void testCountSubJobs_ReturnsTheNumberOfSubJobsOfTheProvidedParentJob_WhenSubJobsExist() {
        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));

        Job subJob1 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        subJob1.setParentJob(parentJob);
        jobDataService.save(subJob1);

        Job subJob2 = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        subJob2.setParentJob(parentJob);
        jobDataService.save(subJob2);

        assertThat(jobDataService.countSubJobs(parentJob)).isEqualTo(2L);
    }

    @Test
    public void testCountSubJobs_ReturnsZero_WhenSubJobsDoNotExist() {

        Job job = new Job();
        job.setId(42L);

        assertThat(jobDataService.countSubJobs(job)).isZero();
    }

    @Test
    public void testGetSubJobIdsWithStatus_ReturnsTheOrderedListOfIdsOfTheSubJobsOfTheProvidedParentJobInTheProvidedStatus_WhenSubJobsExist() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.COMPLETED)
                        .build()
        );
        Job subJob3 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, subJob2, subJob3, parentJob);

        assertThat(jobDataService.getSubJobIdsWithStatus(parentJob, Status.RUNNING))
                .containsExactly(subJob1.getId(), subJob3.getId());

    }

    @Test
    public void testUpdateParentJobOutputs_MergesChildJobOutputsIntoParentJob_WhenChildJobHasOutputs() {
        User owner = userService.save(new User("owner-uid"));
        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(new Job(jobConfig, UUID.randomUUID().toString(), owner));

        Job childJob = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        childJob.setParentJob(parentJob);

        Multimap<String, String> childOutputs = ArrayListMultimap.create();
        childOutputs.put("outputIdOne", "platform://outputProduct/child-job/outputIdOne/file1.zip");
        childOutputs.put("outputIdTwo", "platform://outputProduct/child-job/outputIdTwo/file2.zip");
        childJob.setOutputs(childOutputs);
        childJob = jobDataService.save(childJob);

        Job updatedParentJob = jobDataService.updateParentJobOutputs(childJob);

        assertThat(updatedParentJob.getId()).isEqualTo(parentJob.getId());
        assertThat(updatedParentJob.getOutputs()).isNotNull();
        assertThat(updatedParentJob.getOutputs().get("outputIdOne"))
                .containsExactly("platform://outputProduct/child-job/outputIdOne/file1.zip");
        assertThat(updatedParentJob.getOutputs().get("outputIdTwo"))
                .containsExactly("platform://outputProduct/child-job/outputIdTwo/file2.zip");

        Job retrievedParentJob = jobDataService.getById(parentJob.getId()).get();
        assertThat(retrievedParentJob.getOutputs()).isNotNull();
        assertThat(retrievedParentJob.getOutputs().get("outputIdOne"))
                .containsExactly("platform://outputProduct/child-job/outputIdOne/file1.zip");
        assertThat(retrievedParentJob.getOutputs().get("outputIdTwo"))
                .containsExactly("platform://outputProduct/child-job/outputIdTwo/file2.zip");
    }

    @Test
    public void testUpdateParentJobOutputs_MergesChildJobOutputsIntoExistingParentJobOutputs_WhenParentJobAlreadyHasOutputs() {
        User owner = userService.save(new User("owner-uid"));
        PlatformService svc = svcService.save(new PlatformService("Test Service", owner, "dockerTag"));
        JobConfig jobConfig = jobConfigService.save(new JobConfig(owner, svc));

        Job parentJob = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        Multimap<String, String> existingParentOutputs = ArrayListMultimap.create();
        existingParentOutputs.put("outputIdOne", "platform://outputProduct/child-job-1/outputIdOne/file1.zip");
        parentJob.setOutputs(existingParentOutputs);
        parentJob = jobDataService.save(parentJob);

        Job childJob = new Job(jobConfig, UUID.randomUUID().toString(), owner);
        childJob.setParentJob(parentJob);

        Multimap<String, String> childOutputs = ArrayListMultimap.create();
        childOutputs.put("outputIdOne", "platform://outputProduct/child-job-2/outputIdOne/file2.zip");
        childJob.setOutputs(childOutputs);
        childJob = jobDataService.save(childJob);

        Job updatedParentJob = jobDataService.updateParentJobOutputs(childJob);

        assertThat(updatedParentJob.getId()).isEqualTo(parentJob.getId());
        assertThat(updatedParentJob.getOutputs().get("outputIdOne"))
                .containsExactlyInAnyOrder(
                        "platform://outputProduct/child-job-1/outputIdOne/file1.zip",
                        "platform://outputProduct/child-job-2/outputIdOne/file2.zip");

        assertThat(jobDataService.getById(parentJob.getId()).get().getOutputs().get("outputIdOne"))
                .containsExactlyInAnyOrder(
                        "platform://outputProduct/child-job-1/outputIdOne/file1.zip",
                        "platform://outputProduct/child-job-2/outputIdOne/file2.zip");
    }

    @Test
    public void testCountSubJobStatuses_ReturnsTheCountOfSubJobsForEachStatusOfTheProvidedParentJob() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = createJob(owner, jobConfig)
                .status(Status.ERROR)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.COMPLETED)
                        .build()
        );
        Job subJob3 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.COMPLETED)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, subJob2, subJob3, parentJob);
        assertThat(jobDataService.countSubJobStatuses(parentJob))
                .containsExactlyInAnyOrderEntriesOf(
                        ImmutableMap.of(Status.RUNNING, 1L, Status.COMPLETED, 2L)
                );
    }

    @Test
    public void testCountSubJobStatuses_ReturnsEmptyList_WhenTheProvidedParentJobDoesNotHaveSubJobs() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = createJob(owner, jobConfig)
                .status(Status.ERROR)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob);
        assertThat(jobDataService.countSubJobStatuses(parentJob)).isEmpty();
    }

    @Test
    public void testAllSubJobsCompleted_ReturnsTrue_WhenThereIsOnlyCompletedJobStatus() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = createJob(owner, jobConfig)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job subJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.COMPLETED)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob, parentJob);

        assertThat(jobDataService.allSubJobsCompleted(parentJob)).isTrue();

    }

    @Test
    public void testAllSubJobsCompleted_ReturnsFalse_WhenThereIsCompletedJobStatusAndOtherStatuses() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = createJob(owner, jobConfig)
                .status(Status.ERROR)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.COMPLETED)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, subJob2, parentJob);

        assertThat(jobDataService.allSubJobsCompleted(parentJob)).isFalse();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseIn_ReturnsListOfAllMatchingSubJobsOrderedById_WhenSubJobsPartiallyMatchTheCriteriaAndLimitIsNull() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );
        Job subJob3 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );

        Job subJob4 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, subJob2, subJob3, subJob4, parentJob);

        assertThat(
                jobDataService.findByParentJobAndStatusAndPhaseIn(
                        parentJob,
                        Status.ERROR,
                        Arrays.asList(JobStep.PROCESSING, JobStep.OUTPUT_LIST),
                        null
                )
        ).containsExactly(subJob2, subJob4);

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseIn_ReturnsListOfAllMatchingSubJobs_WhenSubJobsPartiallyMatchTheCriteriaAndLimitIsSet() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob3 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );

        Job subJob4 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, subJob2, subJob3, subJob4, parentJob);

        assertThat(
                jobDataService.findByParentJobAndStatusAndPhaseIn(
                        parentJob,
                        Status.ERROR,
                        Arrays.asList(JobStep.PROCESSING, JobStep.OUTPUT_LIST),
                        1
                )
        ).containsExactly(subJob2);

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseIn_ReturnsListOfAllMatchingSubJobOfTheProvidedParent_WhenMultipleParentJobsExistWithSubJobsAllMatchingTheCriteria() {

        JobConfig jobConfigP1 = createJobConfig("Test Service 1");
        Job parentJob1 = jobDataService.save(
                createJob(owner, jobConfigP1)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJobP1 = jobDataService.save(
                createJob(owner, jobConfigP1)
                        .parentJob(parentJob1)
                        .status(Status.RUNNING)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        JobConfig jobConfigP2 = createJobConfig("Test Service 2");

        Job parentJob2 = jobDataService.save(
                createJob(owner, jobConfigP2)
                        .status(Status.RUNNING)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJobP2 = jobDataService.save(
                createJob(owner, jobConfigP2)
                        .parentJob(parentJob2)
                        .status(Status.RUNNING)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob1, parentJob2, subJobP1, subJobP2);

        assertThat(
                jobDataService.findByParentJobAndStatusAndPhaseIn(
                        parentJob2,
                        Status.RUNNING,
                        Collections.singletonList(JobStep.OUTPUT_LIST),
                        null
                )
        ).containsExactly(subJobP2);
    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseIn_ReturnsEmptyList_WhenNoSubJobsMatchTheCriteria() {

        JobConfig jobConfig = createJobConfig();
        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, parentJob);

        assertThat(jobDataService.findByParentJobAndStatusAndPhaseIn(
                parentJob,
                Status.COMPLETED,
                Collections.singletonList(JobStep.DATA_FETCH),
                null

        )).isEmpty();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseIn_ReturnsEmptyList_WhenParentJobDoesntHaveSubJobs() {

        Job jobParent = jobDataService.save(
                createJob(owner, createJobConfig())
                        .status(Status.ERROR)
                        .phase(JobStep.CREATED)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactly(jobParent);

        assertThat(
                jobDataService.findByParentJobAndStatusAndPhaseIn(
                        jobParent,
                        Status.ERROR,
                        Collections.singletonList(JobStep.CREATED),
                        null
                )
        ).isEmpty();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseIn_ReturnsEmptyList_WhenJobIsNotAParentJob() {

        Job job = jobDataService.save(
                createJob(owner, createJobConfig())
                        .status(Status.ERROR)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactly(job);

        assertThat(
                jobDataService.findByParentJobAndStatusAndPhaseIn(
                        job,
                        Status.ERROR,
                        Collections.singletonList(JobStep.DATA_FETCH),
                        null
                )
        ).isEmpty();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsPageOfMatchingSubJobs_WhenSubJobsPartiallyMatchTheCriteria() {
        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob3 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        Job subJob4 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob, subJob1, subJob2, subJob3, subJob4);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.ERROR,
                Arrays.asList(JobStep.PROCESSING, JobStep.OUTPUT_LIST),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).containsExactly(subJob1, subJob3);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsFirstPageOfMatchingSubJobs_WhenPageSizeIsSmallerThanTotalMatchingSubJobs() {
        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob, subJob1, subJob2);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.ERROR,
                Collections.singletonList(JobStep.PROCESSING),
                PageRequest.of(0, 1)
        );

        assertThat(result.getContent()).containsExactly(subJob1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsLastPageOfMatchingSubJobs_WhenPageSizeIsSmallerThanTotalMatchingSubJobs() {

        JobConfig jobConfig = createJobConfig();
        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob, subJob1, subJob2);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.ERROR,
                Collections.singletonList(JobStep.PROCESSING),
                PageRequest.of(1, 1)
        );

        assertThat(result.getContent()).containsExactly(subJob2);
        assertThat(result.getContent()).doesNotContain(subJob1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsBothPagesWithCorrectContent_WhenMatchingSubJobsSpanTwoPages() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        Job subJobNonMatching = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob, subJob1, subJob2, subJobNonMatching);

        Page<Job> firstPage = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.ERROR,
                Arrays.asList(JobStep.PROCESSING, JobStep.OUTPUT_LIST),
                PageRequest.of(0, 1)
        );

        assertThat(firstPage.getContent()).containsExactly(subJob1);
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();

        Page<Job> secondPage = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.ERROR,
                Arrays.asList(JobStep.PROCESSING, JobStep.OUTPUT_LIST),
                firstPage.nextPageable()
        );

        assertThat(secondPage.getContent()).containsExactly(subJob2);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();
    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsEmptyPage_WhenNoSubJobsMatchTheCriteria() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.COMPLETED)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );
        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, parentJob);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.COMPLETED,
                Collections.singletonList(JobStep.DATA_FETCH),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsOnlySubJobsOfTheProvidedParent_WhenMultipleParentJobsExistWithMatchingSubJobs() {

        JobConfig jobConfigP1 = createJobConfig("Test Service 1");
        Job parentJob1 = jobDataService.save(
                createJob(owner, jobConfigP1)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJobP1 = jobDataService.save(
                createJob(owner, jobConfigP1)
                        .parentJob(parentJob1)
                        .status(Status.RUNNING)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        JobConfig jobConfigP2 = createJobConfig("Test Service 1");
        Job parentJob2 = jobDataService.save(
                createJob(owner, jobConfigP2)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJobP2 = jobDataService.save(
                createJob(owner, jobConfigP2)
                        .parentJob(parentJob2)
                        .status(Status.RUNNING)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob1, parentJob2, subJobP1, subJobP2);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob2,
                Status.RUNNING,
                Collections.singletonList(JobStep.OUTPUT_LIST),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).containsExactly(subJobP2);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsEmptyPage_WhenParentJobDoesNotHaveSubJobs() {

        Job parentJob = createJob(owner, createJobConfig())
                .status(Status.RUNNING)
                .phase(JobStep.PROCESSING)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        assertThat(jobDao.findAll()).containsExactly(parentJob);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                parentJob,
                Status.RUNNING,
                Collections.singletonList(JobStep.PROCESSING),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();

    }

    @Test
    public void testFindByParentJobAndStatusAndPhaseInPaged_ReturnsEmptyPage_WhenJobIsNotAParentJob() {

        Job job = createJob(owner, createJobConfig())
                .status(Status.ERROR)
                .phase(JobStep.DATA_FETCH)
                .build();
        job.setParent(false);

        job = jobDataService.save(job);

        assertThat(jobDao.findAll()).containsExactly(job);

        Page<Job> result = jobDataService.findByParentJobAndStatusAndPhaseInPaged(
                job,
                Status.ERROR,
                Collections.singletonList(JobStep.DATA_FETCH),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    public void testCountByParentJobAndStatusAndPhaseIn_ReturnsTheNumberOfMatchingSubJobs_WhenSubJobsMatchTheCriteria() {

        JobConfig jobConfig = createJobConfig();
        Job parentJob = jobDataService.save(
                createJob(owner, jobConfig)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .phase(JobStep.PROCESSING)
                        .build()
        );
        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.PROCESSING)
                        .build()
        );
        Job subJob3 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );
        Job subJob4 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.ERROR)
                        .phase(JobStep.OUTPUT_LIST)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(subJob1, subJob2, subJob3, subJob4, parentJob);

        assertThat(
                jobDataService.countByParentJobAndStatusAndPhaseIn(
                        parentJob,
                        Status.ERROR,
                        Arrays.asList(JobStep.PROCESSING, JobStep.OUTPUT_LIST)
                )
        ).isEqualTo(2);

    }

    @Test
    public void testCountByParentJobAndStatusAndPhaseIn_ReturnsCountZero_WhenParentJobDoesntHaveSubJobs() {

        Job jobParent = jobDataService.save(
                createJob(owner, createJobConfig())
                        .status(Status.ERROR)
                        .phase(JobStep.CREATED)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactly(jobParent);

        assertThat(
                jobDataService.countByParentJobAndStatusAndPhaseIn(
                        jobParent,
                        Status.ERROR,
                        Collections.singletonList(JobStep.CREATED)
                )
        ).isEqualTo(0);

    }

    @Test
    public void testCountByParentJobAndStatusAndPhaseIn_ReturnsCountZero_WhenJobIsNotAParentJob() {

        Job job = jobDataService.save(
                createJob(owner, createJobConfig())
                        .status(Status.ERROR)
                        .phase(JobStep.DATA_FETCH)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactly(job);

        assertThat(
                jobDataService.countByParentJobAndStatusAndPhaseIn(
                        job,
                        Status.ERROR,
                        Collections.singletonList(JobStep.DATA_FETCH)
                )
        ).isEqualTo(0);

    }

    @Test
    public void testCountByOwnerAndParentFalseAndStatusIn_DoesNotCountParentJobs_WhenParentAndSubJobsShareTheSameStatus() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = createJob(owner, jobConfig)
                .status(Status.RUNNING)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );

        assertThat(jobDao.findAll()).containsExactlyInAnyOrder(parentJob, subJob1, subJob2);
        assertThat(jobDataService.countByOwnerAndParentFalseAndStatusIn(owner, ImmutableList.of(Status.RUNNING)))
                .isEqualTo(2);
    }

    @Test
    public void testCountByOwnerAndParentFalseAndStatusIn_ReturnsZero_WhenAllJobsAreParent() {

        JobConfig jobConfig = createJobConfig();

        Job parent1 = createJob(owner, jobConfig)
                .status(Status.RUNNING)
                .build();
        parent1.setParent(true);

        Job parent2 = createJob(owner, jobConfig)
                .status(Status.RUNNING)
                .build();
        parent2.setParent(true);

        Job parent3 = createJob(owner, jobConfig)
                .status(Status.WAITING)
                .build();
        parent3.setParent(true);

        jobDataService.save(ImmutableList.of(parent1, parent2, parent3));

        assertThat(jobDataService.countByOwnerAndParentFalseAndStatusIn(
                owner, ImmutableList.of(Status.RUNNING, Status.WAITING)))
                .isEqualTo(0);
    }

    @Test
    public void testCountByOwnerAndParentFalseAndStatusIn_CountsOnlySubJobsAndSingleJobs_WhenRunningJobsIncludeParentSubAndSingle() {

        JobConfig jobConfig = createJobConfig();

        Job parentJob = createJob(owner, jobConfig)
                .status(Status.RUNNING)
                .build();
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job subJob1 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );
        Job subJob2 = jobDataService.save(
                createJob(owner, jobConfig)
                        .parentJob(parentJob)
                        .status(Status.RUNNING)
                        .build()
        );

        Job singleJob = createJob(owner, jobConfig)
                .status(Status.RUNNING)
                .build();
        singleJob.setParent(false);
        singleJob = jobDataService.save(singleJob);

        assertThat(jobDao.findAll())
                .containsExactlyInAnyOrder(
                        parentJob, subJob1, subJob2, singleJob);
        assertThat(jobDataService.countByOwnerAndParentFalseAndStatusIn(owner, ImmutableList.of(Status.RUNNING)))
                .isEqualTo(3);
    }

    private List<Job> getSubJobs(Job parentJob) {
        return jobDataService.findByIds(jobDataService.getSubJobIds(parentJob));
    }

    private JobConfig createJobConfig() {
        return createJobConfig("Test Service");
    }

    private JobConfig createJobConfig(String platformServiceName) {
        PlatformService svc = svcService.save(new PlatformService(platformServiceName, owner, "dockerTag"));
        return jobConfigService.save(new JobConfig(owner, svc));
    }

}
