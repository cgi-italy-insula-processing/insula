package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.StopJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import javax.jms.JMSException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class KubernetesCoreDispatcherTest {

    private final LegacyWorkflowEventsDispatcher legacyWorkflowEventsDispatcher = mock(LegacyWorkflowEventsDispatcher.class);
    private final KubernetesWorkerJobUpdatesManager kubernetesWorkerJobUpdatesManager = mock(KubernetesWorkerJobUpdatesManager.class);

    private final InOrder inOrder = Mockito.inOrder(legacyWorkflowEventsDispatcher, kubernetesWorkerJobUpdatesManager);

    private KubernetesCoreDispatcher kubernetesCoreDispatcher;

    @Before
    public void setUp() {
        kubernetesCoreDispatcher = new KubernetesCoreDispatcher(legacyWorkflowEventsDispatcher, kubernetesWorkerJobUpdatesManager);
    }

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReceiveJobSpec_DispatchesWorkflowStartRequest_WhenJobSpecKindIsWorkflow() throws JMSException {

        JobSpec jobSpec = createJobSpec()
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                .setId("job-id")
                .setIntJobId("123")
                .build()).build();

        kubernetesCoreDispatcher.receiveJobSpec(jobSpec);

        inOrder.verify(kubernetesWorkerJobUpdatesManager)
            .onWorkflowStartRequested(new KubernetesWorkerJob("job-id", "123"), jobSpec);
    }

    @Test
    public void testReceiveJobSpec_DoesNotDispatchRequest_WhenJobSpecKindIsNotWorkflow() throws JMSException {


        JobSpec jobSpec = createJobSpec()
            .setKind(Kind.INTERACTIVE)
            .setJob(Job.newBuilder()
                .setId("job-id")
                .setIntJobId("123")
                .build()).build();

        kubernetesCoreDispatcher.receiveJobSpec(jobSpec);

        inOrder.verify(kubernetesWorkerJobUpdatesManager, times(0))
            .onWorkflowStartRequested(any(), any());
    }

    @Test
    public void testReceiveStopJobRequest_DispatchesWorkflowStopRequest_WhenRequestKindIsWorkflow() throws JMSException {

        StopJob stopJob = StopJob.newBuilder()
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                .setId("job-id")
                .setIntJobId("123")
                .build()).build();


        kubernetesCoreDispatcher.receiveStopJobRequest(stopJob);

        inOrder.verify(kubernetesWorkerJobUpdatesManager)
            .onWorkflowStopRequested(new KubernetesWorkerJob("job-id", "123"));
    }

    @Test
    public void testReceiveStopJobRequest_DoesNotDispatchWorkflowStopRequest_WhenRequestKindIsNotWorkflow() throws JMSException {

        StopJob stopJob = StopJob.newBuilder()
            .setKind(Kind.INTERACTIVE)
            .setJob(Job.newBuilder()
                .setId("job-id")
                .setIntJobId("123")
                .build()).build();

        kubernetesCoreDispatcher.receiveStopJobRequest(stopJob);

        inOrder.verify(kubernetesWorkerJobUpdatesManager, times(0))
            .onWorkflowStopRequested(any());
    }

    @Test
    public void testReceiveKubernetesEvent_DispatchesEventToLegacyWorkflowEventsDispatcher() {

        K8SEvent event = K8SEvent.newBuilder().build();

        kubernetesCoreDispatcher.receiveKubernetesEvent(event);

        inOrder.verify(legacyWorkflowEventsDispatcher)
            .dispatch(event);
    }

    private JobSpec.Builder createJobSpec() {
        return JobSpec.newBuilder()
            .setService(Service.newBuilder()
                .setDockerImageTag("test:1.1")
                .setId("service-id")
                .setName("service-name")
                .build());

    }
}