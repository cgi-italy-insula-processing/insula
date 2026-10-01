package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService.Type;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.StopJob;

/**
 * Test class that verifies the interaction between the PlatformJobStopRequestSubmitter and the JMS queues.
 * Interactions with other services or message consumers are mocked.
 *
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class PlatformJobStopRequestSubmitterQueuesIT {

    @Autowired
    private QueueService queueService;

    @Autowired
    private PlatformJobStopRequestSubmitter platformJobStopRequestSubmitter;

    @Test
    public void testStopProcessorJobSendsStopJobMessageToJobStopRequestsQueue() {

        User owner = new User("user");
        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setType(Type.PROCESSOR);
        Job modelJob = new Job(new JobConfig(owner, service), "extId", owner);
        modelJob.setId(20L);
        modelJob.setWorkerId("theWorkerId");

        platformJobStopRequestSubmitter.stopJob(modelJob);

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(queueService.getQueueLength(
                        ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isNotEqualTo(0));

        assertThat(queueService.getQueueLength(
                ProcessingCoreQueueNames.JOB_STOP_REQUESTS)).isEqualTo(1);

        Message response = queueService
                .receiveNoWait(ProcessingCoreQueueNames.JOB_STOP_REQUESTS);

        assertThat((StopJob) response.getPayload())
                .isEqualTo(StopJob.newBuilder()
                        .setJob(com.cgi.eoss.platform.rpc.Job.newBuilder()
                                .setId("extId")
                                .setIntJobId("20")
                                .setUserId("user")
                                .setServiceId("serviceName")
                                .build())
                        .build());

        assertThat(response.getHeaders().size()).isEqualTo(2);
        assertThat(response.getHeaders().get("jobId")).isEqualTo("20");
        assertThat(response.getHeaders().get("workerId")).isEqualTo("theWorkerId");
    }
}