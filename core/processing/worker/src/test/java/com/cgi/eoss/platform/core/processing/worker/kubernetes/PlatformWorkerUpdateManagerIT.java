package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import com.cgi.eoss.platform.core.processing.worker.WorkerCoreTestConfig;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.processing.worker.WorkerCoreConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.worker.JobError;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { WorkerCoreConfig.class, WorkerCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-worker-core-k8.properties")
public class PlatformWorkerUpdateManagerIT {

    @Autowired
    private PlatformWorkerUpdateManager platformWorkerUpdateManager;

    @Autowired
    private QueueService queueService;

    @Autowired
    private String workerId;

    @Test
    public void testSendJobUpdateMessageToJobUpdatesQueue() {

        JobError payload = JobError.newBuilder().setErrorDescription("Unrecognized job type").build();

        platformWorkerUpdateManager.jobUpdate("10", payload);

        await().atMost(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(queueService.getQueueLength(
                                ProcessingCoreQueueNames.JOB_UPDATES)).isNotEqualTo(0));

        assertThat(queueService.getQueueLength(
                    ProcessingCoreQueueNames.JOB_UPDATES)).isEqualTo(1);

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);
        assertThat((JobError) message.getPayload()).isEqualTo(payload);

        assertThat(message.getHeaders().size()).isEqualTo(2);
        assertThat(message.getHeaders().get("workerId")).isEqualTo(workerId);
        assertThat(message.getHeaders().get("jobId")).isEqualTo("10");
    }
}
