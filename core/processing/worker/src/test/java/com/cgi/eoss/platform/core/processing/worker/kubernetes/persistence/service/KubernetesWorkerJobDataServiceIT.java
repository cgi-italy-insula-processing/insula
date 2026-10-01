package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service;

import com.cgi.eoss.platform.core.processing.worker.WorkerCoreTestConfig;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.WorkerCoreDefaultJobsPersistenceConfig;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.WorkerCoreJobsPersistenceConfig;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.testutils.PersistenceTestUtils;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.TransactionSystemException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { WorkerCoreJobsPersistenceConfig.class, WorkerCoreDefaultJobsPersistenceConfig.class, WorkerCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-worker-core-k8.properties")
public class KubernetesWorkerJobDataServiceIT {

    @Autowired
    private PersistenceTestUtils persistenceTestUtils;

    @Autowired
    private KubernetesWorkerJobDataService kubernetesWorkerJobDataService;

    @Before
    public void setUp() {
        persistenceTestUtils.cleanDatabase();
        persistenceTestUtils.assertDbIsEmpty();
    }

    @After
    public void shutdown() {
        persistenceTestUtils.cleanDatabase();
        persistenceTestUtils.assertDbIsEmpty();
    }

    @Test
    public void testSaveKubernetesWorkerJob() {

        OffsetDateTime startTime = OffsetDateTime.now();
        OffsetDateTime endTime = startTime.plusMinutes(1L);

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");
        kubernetesWorkerJob.setStart(startTime);
        kubernetesWorkerJob.setEnd(endTime);
        kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);

        KubernetesWorkerJob savedKubernetesWorkerJob = kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
        assertThat(savedKubernetesWorkerJob.getJobId()).isEqualTo("JobId");
        assertThat(savedKubernetesWorkerJob.getIntJobId()).isEqualTo("IntJobId");
        assertThat(savedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(savedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(savedKubernetesWorkerJob.getEnd()).isEqualTo(endTime);

        List<KubernetesWorkerJob> kubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(kubernetesWorkerJobs).hasSize(1);

        KubernetesWorkerJob retrievedKubernetesWorkerJob = kubernetesWorkerJobs.get(0);
        assertThat(retrievedKubernetesWorkerJob.getJobId()).isEqualTo("JobId");
        assertThat(retrievedKubernetesWorkerJob.getIntJobId()).isEqualTo("IntJobId");
        assertThat(retrievedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(retrievedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(retrievedKubernetesWorkerJob.getEnd()).isEqualTo(endTime);

    }

    @Test
    public void testSaveKubernetesWorkerJobStatusDetaultsToStartingWhenNotSet() {

        OffsetDateTime startTime = OffsetDateTime.now();
        OffsetDateTime endTime = startTime.plusMinutes(1L);

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");
        kubernetesWorkerJob.setStart(startTime);
        kubernetesWorkerJob.setEnd(endTime);

        kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        List<KubernetesWorkerJob> kubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(kubernetesWorkerJobs).hasSize(1);

        KubernetesWorkerJob kubernetesWorkerJobSaved = kubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJobSaved.getJobId()).isEqualTo("JobId");
        assertThat(kubernetesWorkerJobSaved.getIntJobId()).isEqualTo("IntJobId");
        assertThat(kubernetesWorkerJobSaved.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTING);
        assertThat(kubernetesWorkerJobSaved.getStart()).isEqualTo(startTime);
        assertThat(kubernetesWorkerJobSaved.getEnd()).isEqualTo(endTime);

    }

    @Test
    public void testSave_PersistsKubernetesWorkerJobWithDefaultGroupId_WhenGroupIdIsNotSet() {
        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");

        kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        List<KubernetesWorkerJob> kubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(kubernetesWorkerJobs).hasSize(1);

        KubernetesWorkerJob kubernetesWorkerJobSaved = kubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJobSaved.getJobId()).isEqualTo("JobId");
        assertThat(kubernetesWorkerJobSaved.getGroupId()).isEqualTo(0L);
    }

    @Test
    public void testSave_PersistsKubernetesWorkerJobWithCustomGroupId_WhenGroupIdIsSet() {
        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");
        kubernetesWorkerJob.setGroupId(100L);

        kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        List<KubernetesWorkerJob> kubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(kubernetesWorkerJobs).hasSize(1);

        KubernetesWorkerJob kubernetesWorkerJobSaved = kubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJobSaved.getJobId()).isEqualTo("JobId");
        assertThat(kubernetesWorkerJobSaved.getGroupId()).isEqualTo(100L);
    }

    @Test
    public void testSaveKubernetesWorkerJobWithNullJobIdThrows() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId(null);
        kubernetesWorkerJob.setIntJobId("IntJobId");
        kubernetesWorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJob.setEnd(OffsetDateTime.now());

        try {
            kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
            fail("JpaSystemException violation did not throw");
        } catch (JpaSystemException e) {
            assertThat(e.getMessage()).contains("ids for this class must be manually assigned before calling save()");
        }
    }

    @Test
    public void testSaveKubernetesWorkerJobWithEmptyJobIdThrows() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("");
        kubernetesWorkerJob.setIntJobId("IntJobId");
        kubernetesWorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJob.setEnd(OffsetDateTime.now());

        try {
            kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
            fail("TransactionSystemException violation did not throw");
        } catch (TransactionSystemException e) {
            assertThat(e.getMessage()).contains("Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction");
        }
    }

    @Test
    public void testSaveKubernetesWorkerJobWithNullIntJobIdThrows() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId(null);
        kubernetesWorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJob.setEnd(OffsetDateTime.now());

        try {
            kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
            fail("TransactionSystemException violation did not throw");

        } catch (DataIntegrityViolationException e) {
            assertThat(e.getMessage()).contains("constraint [null]; nested exception is org.hibernate.exception.ConstraintViolationException: could not execute statement");
        }
    }

    @Test
    public void testSaveKubernetesWorkerJobWithEmptyIntJobIdThrows() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("");
        kubernetesWorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJob.setEnd(OffsetDateTime.now());

        try {
            kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
            fail("TransactionSystemException violation did not throw");
        } catch (TransactionSystemException e) {
            assertThat(e.getMessage()).contains("Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction");
        }
    }

    @Test
    public void testSaveKubernetesWorkerJobWithNullStatusThrows() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");
        kubernetesWorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJob.setEnd(OffsetDateTime.now());
        kubernetesWorkerJob.setStatus(null);

        try {
            kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
            fail("DataIntegrityViolationException violation did not throw");

        } catch (DataIntegrityViolationException e) {

            assertThat(e.getMessage()).contains("could not execute statement; SQL [n/a]; constraint [null]; nested exception is org.hibernate.exception.ConstraintViolationException: could not execute statement");
        }
    }

    @Test
    public void testFindByJobIdReturnsKubernetesWorkerJobWithTheProvidedId() {

        OffsetDateTime startTime = OffsetDateTime.now();
        OffsetDateTime endTime = startTime.plusSeconds(1L);

        {
            KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
            kubernetesWorkerJob.setJobId("JobId");
            kubernetesWorkerJob.setIntJobId("IntJobId");
            kubernetesWorkerJob.setStart(startTime);
            kubernetesWorkerJob.setEnd(endTime);
            kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTED);
            kubernetesWorkerJobDataService.save(kubernetesWorkerJob);
        }

        assertThat(persistenceTestUtils.findAllKubernetesWorkerJob()).hasSize(1);

        KubernetesWorkerJob retrievedKubernetesWorkerJob = kubernetesWorkerJobDataService.findByJobId("JobId").get();
        assertThat(retrievedKubernetesWorkerJob.getJobId()).isEqualTo("JobId");
        assertThat(retrievedKubernetesWorkerJob.getIntJobId()).isEqualTo("IntJobId");
        assertThat(retrievedKubernetesWorkerJob.getStart()).isEqualTo(startTime);
        assertThat(retrievedKubernetesWorkerJob.getEnd()).isEqualTo(endTime);
        assertThat(retrievedKubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
    }

    @Test
    public void testFindByJobIdReturnsEmptyOptionalWhenJobWithTheProvidedIdDoesNotExist() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");

        kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        assertThat(persistenceTestUtils.findAllKubernetesWorkerJob()).hasSize(1);

        assertThat(kubernetesWorkerJobDataService.findByJobId("NotExistingJobId").isPresent()).isFalse();
    }

    @Test
    public void testFindByJobIdReturnsEmptyOptionalWhenDBIsEmpty() {
        assertThat(kubernetesWorkerJobDataService.findByJobId("JobId").isPresent()).isFalse();
    }

    @Test
    public void testFindByIdOrDefault_ReturnsKubernetesWorkerJobWithTheProvidedId(){

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");

        kubernetesWorkerJob = kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        assertThat(persistenceTestUtils.findAllKubernetesWorkerJob()).hasSize(1);

        KubernetesWorkerJob kubernetesWorkerJobFromDb = kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJob);

        assertThat(kubernetesWorkerJobFromDb.getJobId()).isEqualTo("JobId");
        assertThat(kubernetesWorkerJobFromDb.getIntJobId()).isEqualTo("IntJobId");

    }

    @Test
    public void testFindByIdOrDefault_ReturnsDefaultValues_WhenJobWithTheProvidedIdDoesNotExist(){

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("JobId");
        kubernetesWorkerJob.setIntJobId("IntJobId");

        assertThat(persistenceTestUtils.findAllKubernetesWorkerJob()).hasSize(0);

        KubernetesWorkerJob kubernetesWorkerJobFromDb = kubernetesWorkerJobDataService.findByIdOrDefault(kubernetesWorkerJob);

        assertThat(kubernetesWorkerJobFromDb.getJobId()).isEqualTo("JobId");
        assertThat(kubernetesWorkerJobFromDb.getIntJobId()).isEqualTo("IntJobId");
    }


    @Test
    public void testFindByStatusInAndJobType_ReturnsKubernetesWorkerJobsWithProvidedJobTypeAndStatusInProvidedList () {
        KubernetesWorkerJob k8sWorkflowStarted = new KubernetesWorkerJob();
        k8sWorkflowStarted.setJobId("k8sWorkflowStarted");
        k8sWorkflowStarted.setIntJobId("IntJobId1");
        k8sWorkflowStarted.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        k8sWorkflowStarted.setStatus(KubernetesWorkerJob.Status.STARTED);
        kubernetesWorkerJobDataService.save(k8sWorkflowStarted);

        KubernetesWorkerJob k8sWorkflowStarting = new KubernetesWorkerJob();
        k8sWorkflowStarting.setJobId("k8sWorkflowStarting");
        k8sWorkflowStarting.setIntJobId("IntJobId2");
        k8sWorkflowStarting.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        k8sWorkflowStarting.setStatus(KubernetesWorkerJob.Status.STARTING);
        kubernetesWorkerJobDataService.save(k8sWorkflowStarting);

        KubernetesWorkerJob k8sWorkflowStopped = new KubernetesWorkerJob();
        k8sWorkflowStopped.setJobId("k8sWorkflowStopped");
        k8sWorkflowStopped.setIntJobId("IntJobId3");
        k8sWorkflowStopped.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        k8sWorkflowStopped.setStatus(KubernetesWorkerJob.Status.STOPPED);
        kubernetesWorkerJobDataService.save(k8sWorkflowStopped);

        KubernetesWorkerJob k8sIntAppStarting = new KubernetesWorkerJob();
        k8sIntAppStarting.setJobId("k8sIntAppStarting");
        k8sIntAppStarting.setIntJobId("IntJobId4");
        k8sIntAppStarting.setJobType(KubernetesWorkerJob.JobType.INTERACTIVE_APPLICATION);
        k8sIntAppStarting.setStatus(KubernetesWorkerJob.Status.STARTING);
        kubernetesWorkerJobDataService.save(k8sIntAppStarting);

        KubernetesWorkerJob expectedRetrievedK8sStartedJob = new KubernetesWorkerJob();
        expectedRetrievedK8sStartedJob.setJobId("k8sWorkflowStarted");
        expectedRetrievedK8sStartedJob.setIntJobId("IntJobId1");
        expectedRetrievedK8sStartedJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        expectedRetrievedK8sStartedJob.setStatus(KubernetesWorkerJob.Status.STARTED);
        KubernetesWorkerJob expectedRetrievedK8sStartingJob = new KubernetesWorkerJob();
        expectedRetrievedK8sStartingJob.setJobId("k8sWorkflowStarting");
        expectedRetrievedK8sStartingJob.setIntJobId("IntJobId2");
        expectedRetrievedK8sStartingJob.setJobType(KubernetesWorkerJob.JobType.WORKFLOW);
        expectedRetrievedK8sStartingJob.setStatus(KubernetesWorkerJob.Status.STARTING);

        assertThat(kubernetesWorkerJobDataService.findByStatusInAndJobType(
                ImmutableList.of(KubernetesWorkerJob.Status.STARTING, KubernetesWorkerJob.Status.STARTED),
                KubernetesWorkerJob.JobType.WORKFLOW))
                .containsExactlyInAnyOrder(expectedRetrievedK8sStartedJob, expectedRetrievedK8sStartingJob);
    }

}
