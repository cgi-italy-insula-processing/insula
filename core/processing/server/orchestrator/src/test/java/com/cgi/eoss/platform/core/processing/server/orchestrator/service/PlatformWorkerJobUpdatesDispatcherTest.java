package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.rpc.GrpcUtil;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;
import com.google.protobuf.Timestamp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import javax.jms.ObjectMessage;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class PlatformWorkerJobUpdatesDispatcherTest {

    private final WorkerJobUpdatesManager updatesManager = mock(WorkerJobUpdatesManager.class);
    private final JobDataService jobDataService = mock(JobDataService.class);
    private final ObjectMessage objectMessage = mock(ObjectMessage.class);
    private PlatformWorkerJobUpdatesDispatcher updatesDispatcher;
    private InOrder inOrder;

    @Before
    public void init() {
        updatesDispatcher = new PlatformWorkerJobUpdatesDispatcher(jobDataService, updatesManager);
        inOrder = inOrder(updatesManager, jobDataService);
    }

    @After
    public void after() {
        verifyNoMoreInteractions(updatesManager, jobDataService);
    }

    @Test
    public void testReceiveJobUpdateMessage_RethrowsTheOriginalException_WhenErrorOccursDuringDatabaseInteraction() throws Exception {
        Long intJobId = 11L;

        when(jobDataService.refreshFull(intJobId)).thenThrow(new RuntimeException("test exception"));
        when(objectMessage.getObject()).thenReturn(JobEvent
                .newBuilder()
                .setJobEventType(JobEventType.PROCESSING_COMPLETED)
                .build());

        assertThatThrownBy(() -> updatesDispatcher.receiveJobUpdateMessage(
                objectMessage,
                intJobId.toString(),
                intJobId.toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test exception");
        inOrder.verify(jobDataService, times(1)).refreshFull(intJobId);
    }

    @Test
    public void testReceiveJobUpdateMessage_RethrowsTheOriginalException_WhenErrorOccursDuringMessageProcessing() throws Exception {
        Long intJobId = 11L;
        Job job = createJob(intJobId);
        Timestamp timestamp = GrpcUtil.timestampFromInstant(Instant.now());

        when(jobDataService.refreshFull(intJobId)).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("test exception")).when(updatesManager).onJobProcessingCompleted(job, timestamp);
        when(objectMessage.getObject()).thenReturn(JobEvent.newBuilder()
                .setTimestamp(timestamp)
                .setJobEventType(JobEventType.PROCESSING_COMPLETED)
                .build());

        assertThatThrownBy(() -> updatesDispatcher.receiveJobUpdateMessage(
                        objectMessage,
                        intJobId.toString(),
                        intJobId.toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test exception");
        inOrder.verify(jobDataService, times(1)).refreshFull(intJobId);
        inOrder.verify(updatesManager, times(1)).onJobProcessingCompleted(job, timestamp);
    }

    private Job createJob(Long jobId) {
        Job job = new Job();
        job.setId(jobId);
        job.setConfig(new JobConfig(new User(), new PlatformService()));
        return job;
    }
}
