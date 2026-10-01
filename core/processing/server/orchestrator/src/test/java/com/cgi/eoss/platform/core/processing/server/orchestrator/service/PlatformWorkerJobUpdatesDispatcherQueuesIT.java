package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreDefaultConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.processing.server.model.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.core.processing.rpc.GrpcUtil;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;
import com.google.protobuf.Timestamp;

/**
 * Test class that verifies the interaction between the PlatformWorkerJobUpdatesDispatcher and the JMS queues.
 * Interactions with other services or message consumers are mocked.
 * Scheduling is disabled in order to prevent side effects from scheduled consumers
 *
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreDefaultConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties", properties = "platform.orchestrator.scheduling.enabled=false")
public class PlatformWorkerJobUpdatesDispatcherQueuesIT {

    @MockBean
    private JobDataService jobDataService;

    @MockBean
    private WorkerJobUpdatesManager workerJobUpdatesManager;

    @Autowired
    private QueueService queueService;

    private InOrder inOrder;

    @Before
    public void setUp() {
        inOrder = inOrder(jobDataService, workerJobUpdatesManager);
    }

    @After
    public void shutdown() {
        verifyNoMoreInteractions(jobDataService, workerJobUpdatesManager);
    }

    @Test
    public void testDelegateToPlatformWorkerJobUpdatesManagerOnJobProcessingCompletedWhenProcessingCompletedJobEventIsReceived() {
        Long intJobId = 11L;
        Job job = new Job();
        job.setId(intJobId);
        job.setConfig(new JobConfig(new User(), new PlatformService()));

        when(jobDataService.refreshFull(intJobId)).thenReturn(Optional.of(job));

        Map<String, Object> headers = new HashMap<>();
        headers.put("jobId", intJobId.toString());
        headers.put("workerId", intJobId.toString());

        Timestamp timestamp = GrpcUtil.timestampFromInstant(Instant.now());
        Message message = Message.builder()
            .payload(JobEvent.newBuilder()
                .setJobEventType(JobEventType.PROCESSING_COMPLETED)
                .setTimestamp(timestamp)
                .build())
            .headers(headers)
            .build();

        queueService.send(ProcessingCoreQueueNames.JOB_UPDATES, message);

        await().atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> inOrder.verify(jobDataService, times(1)).refreshFull(intJobId));

        await().atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> inOrder.verify(workerJobUpdatesManager, times(1)).onJobProcessingCompleted(job, timestamp));
    }

    @Test
    public void testDispatchJobUpdate_MessageIsDiscarded_WhenJobDoesNotExist() {
        Long fakeIntJobId = 12345L;
        Job job = new Job();
        job.setId(fakeIntJobId);
        job.setConfig(new JobConfig(new User(), new PlatformService()));
        when(jobDataService.refreshFull(fakeIntJobId)).thenReturn(Optional.empty());

        Map<String, Object> headers = new HashMap<>();
        headers.put("jobId", fakeIntJobId.toString());
        headers.put("workerId", fakeIntJobId.toString());

        Timestamp timestamp = GrpcUtil.timestampFromInstant(Instant.now());
        Message message = Message.builder()
            .payload(JobEvent.newBuilder()
                .setJobEventType(JobEventType.PROCESSING_COMPLETED)
                .setTimestamp(timestamp)
                .build())
            .headers(headers)
            .build();

        queueService.send(ProcessingCoreQueueNames.JOB_UPDATES, message);

        await().atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> inOrder.verify(jobDataService, times(1))
                .refreshFull(fakeIntJobId));

        await().atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> inOrder.verify(workerJobUpdatesManager, times(0))
                .onJobProcessingCompleted(job, timestamp));

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_UPDATES)).isEqualTo(0);
    }

}
