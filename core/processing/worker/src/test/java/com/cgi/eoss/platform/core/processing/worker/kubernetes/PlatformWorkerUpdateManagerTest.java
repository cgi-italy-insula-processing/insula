package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class PlatformWorkerUpdateManagerTest {

    private final QueueService queueService = mock(QueueService.class);
    private final PlatformWorkerUpdateManager platformWorkerUpdateManager = new PlatformWorkerUpdateManager(queueService, "workerId");
    private final InOrder inOrder = inOrder(queueService);

    @Before
    public void init() {
    }

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testJobUpdateSendsWithWorkerJobAsParameter() {

        platformWorkerUpdateManager.jobUpdate("42", "TheBody");

        Map<String, Object> headersMap = new HashMap<>();
        headersMap.put("workerId", "workerId");
        headersMap.put("jobId", "42");

        inOrder.verify(queueService, times(1)).sendObject(ProcessingCoreQueueNames.JOB_UPDATES, headersMap, "TheBody");
    }

    @Test
    public void testJobUpdateWithJobIntIdAsParameter() {

        platformWorkerUpdateManager.jobUpdate("45", "TheBody");

        Map<String, Object> headersMap = new HashMap<>();
        headersMap.put("workerId", "workerId");
        headersMap.put("jobId", "45");

        inOrder.verify(queueService, times(1)).sendObject(ProcessingCoreQueueNames.JOB_UPDATES, headersMap, "TheBody");
    }

}
