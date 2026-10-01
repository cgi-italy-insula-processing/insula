package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class PlatformJobSubmitterTest {

    private static final String DESTINATION_QUEUE_NAME = "test-queue";
    private static final long JOB_ID = 12L;
    private static final int EXPECTED_PRIORITY = 1;

    private final User owner = new User("platform-test-user");

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final QueueService queueService = mock(QueueService.class);
    private final JobPriorityCalculator jobPriorityCalculator = mock(JobPriorityCalculator.class);

    private final JobSpecProducer notSupportingProducer = mock(JobSpecProducer.class);
    private final JobSpecProducer supportingProducer = mock(JobSpecProducer.class);
    private final JobSpecProducer laterProducer = mock(JobSpecProducer.class);

    private final JobSpecProducer processorJobSpecProducer = mock(JobSpecProducer.class);
    private final JobSpecProducer applicationJobSpecProducer = mock(JobSpecProducer.class);

    private InOrder inOrder;

    @Before
    public void init(){
        inOrder = inOrder(
                notSupportingProducer,
                supportingProducer,
                laterProducer,
                processorJobSpecProducer,
                applicationJobSpecProducer,
                jobPriorityCalculator,
                queueService,
                jobDataService);
    }

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSubmitJob_UsesTheFirstSupportingProducerAndDoesNotConsultLaterProducers() {

        PlatformService service = createService(PlatformService.Type.APPLICATION);
        Job job = createJob(service);
        JobSubmissionRequest jobSubmissionRequest = createJobSubmissionRequest(job);
        JobSpec jobSpec = JobSpec.newBuilder().build();

        when(jobPriorityCalculator.calculateJobPriority(job)).thenReturn(EXPECTED_PRIORITY);
        when(notSupportingProducer.supports(service)).thenReturn(false);
        when(supportingProducer.supports(service)).thenReturn(true);
        when(supportingProducer.produceJobSpec(jobSubmissionRequest)).thenReturn(jobSpec);

        PlatformJobSubmitter platformJobSubmitter = createPlatformJobSubmitterWith(
                ImmutableSet.of(notSupportingProducer, supportingProducer, laterProducer));

        platformJobSubmitter.submitJob(jobSubmissionRequest);

        inOrder.verify(notSupportingProducer).supports(service);
        inOrder.verify(supportingProducer).supports(service);
        inOrder.verify(supportingProducer).produceJobSpec(jobSubmissionRequest);
        inOrder.verify(jobPriorityCalculator).calculateJobPriority(job);
        inOrder.verify(laterProducer, never()).supports(service);

        Map<String, Object> expectedHeaders = new HashMap<>();
        expectedHeaders.put("jobId", String.valueOf(JOB_ID));

        inOrder.verify(queueService).sendObject(
                DESTINATION_QUEUE_NAME, expectedHeaders, jobSpec, EXPECTED_PRIORITY);
    }

    @Test
    public void testSubmitJob_ThrowsIllegalStateException_WhenNoProducerSupportsTheService() {

        PlatformService service = createService(PlatformService.Type.PROCESSOR);
        Job job = createJob(service);
        JobSubmissionRequest jobSubmissionRequest = createJobSubmissionRequest(job);

        when(processorJobSpecProducer.supports(service)).thenReturn(false);
        when(applicationJobSpecProducer.supports(service)).thenReturn(false);
        when(jobPriorityCalculator.calculateJobPriority(job)).thenReturn(EXPECTED_PRIORITY);

        PlatformJobSubmitter platformJobSubmitter = createPlatformJobSubmitterWith(
                ImmutableSet.of(processorJobSpecProducer, applicationJobSpecProducer));

        assertThatThrownBy(() -> platformJobSubmitter.submitJob(jobSubmissionRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No JobSpecProducer supports service type PROCESSOR");

        inOrder.verify(processorJobSpecProducer).supports(service);
        inOrder.verify(applicationJobSpecProducer).supports(service);
    }

    private PlatformJobSubmitter createPlatformJobSubmitterWith(Set<JobSpecProducer> jobSpecProducers) {
        return new PlatformJobSubmitter(
                jobSpecProducers,
                jobPriorityCalculator,
                queueService, DESTINATION_QUEUE_NAME);
    }

    private PlatformService createService(PlatformService.Type type) {
        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setType(type);
        return service;
    }

    private JobSubmissionRequest createJobSubmissionRequest(Job job) {
        return JobSubmissionRequest.builder().job(job).build();
    }

    private Job createJob(PlatformService service) {
        Job job = new Job(new JobConfig(owner, service), "extId", owner);
        job.setId(JOB_ID);
        return job;
    }
}