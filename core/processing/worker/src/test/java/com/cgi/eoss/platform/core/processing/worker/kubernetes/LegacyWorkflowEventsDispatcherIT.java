package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;

import com.cgi.eoss.platform.core.processing.worker.WorkerCoreConfig;
import com.cgi.eoss.platform.core.processing.worker.WorkerCoreTestConfig;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.testutils.PersistenceTestUtils;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.core.processing.rpc.GrpcUtil;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import com.cgi.eoss.platform.rpc.worker.JobError;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import io.fabric8.mockwebserver.DefaultMockServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { WorkerCoreConfig.class, WorkerCoreTestConfig.class })
@TestPropertySource(locations = { "classpath:test-worker-core-k8.properties" })
public class LegacyWorkflowEventsDispatcherIT {

    private static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "kubernetes");

    private static final String JOB_ID = "theJobId";
    private static final String INT_JOB_ID = "10";

    @Autowired
    private QueueService queueService;

    @Autowired
    private PersistenceTestUtils persistenceTestUtils;

    @Value("${platform.kubernetes.port}")
    private int port;

    @Autowired
    private LegacyWorkflowEventsDispatcher legacyWorkflowEventsDispatcher;

    private DefaultMockServer webServer;

    private OffsetDateTime testStartTime;

    @Before
    public void setUp() {

        persistenceTestUtils.cleanDatabase();
        persistenceTestUtils.assertDbIsEmpty();

        webServer = new DefaultMockServer();
        webServer.start(port);

        initJobUpdatesQueue();

        testStartTime = OffsetDateTime.now();
    }

    @After
    public void shutdown() {

        persistenceTestUtils.cleanDatabase();
        persistenceTestUtils.assertDbIsEmpty();

        webServer.shutdown();
    }

    @Test
    public void testDispatch_ReturnsTrue_WhenEventIsWorkflowRunning() {

        assertThat(
            legacyWorkflowEventsDispatcher.dispatch(createWorkflowEvent(K8SEventType.K8S_WORKFLOW_RUNNING))
        ).isTrue();

        assertThat(webServer.getRequestCount()).isZero();

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);
        JobEvent jobEvent = (JobEvent) message.getPayload();
        assertThat(message.getHeaders()).hasSize(2);
        assertThat(message.getHeaders().get("jobId")).isEqualTo(INT_JOB_ID);
        assertThat(message.getHeaders().get("workerId")).isEqualTo("workerId");
        assertThat(jobEvent.getJobEventType()).isEqualTo(JobEventType.PROCESSING_STARTED);
        assertThat(GrpcUtil.offsetDateTimeFromTimestamp(jobEvent.getTimestamp())).isBetween(testStartTime, OffsetDateTime.now());
        assertNoMoreJobUpdates();

        List<KubernetesWorkerJob> allKubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(allKubernetesWorkerJobs).hasSize(1);
        KubernetesWorkerJob kubernetesWorkerJob = allKubernetesWorkerJobs.get(0);

        assertThat(kubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(kubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(kubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(kubernetesWorkerJob.getStart()).isNull();
        assertThat(kubernetesWorkerJob.getEnd()).isNull();
    }

    @Test
    public void testDispatch_ReturnsTrue_WhenEventIsWorkflowCompleted() throws InterruptedException {

        createWorkflowDeletionExpectations();

        assertThat(
            legacyWorkflowEventsDispatcher.dispatch(createWorkflowEvent(K8SEventType.K8S_WORKFLOW_COMPLETED))
        ).isTrue();

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        RecordedRequest recordedGetWorkflowsRequest = webServer.takeRequest();
        assertThat(recordedGetWorkflowsRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedGetWorkflowsRequest.getPath()).isEqualTo("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3DtheJobId&watch=false");
        RecordedRequest recordedDeleteWorkflowRequest = webServer.takeRequest();
        assertThat(recordedDeleteWorkflowRequest.getMethod()).isEqualTo("DELETE");
        assertThat(recordedDeleteWorkflowRequest.getPath()).isEqualTo("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows/workflow-cdb67");

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);
        JobEvent jobEvent = (JobEvent) message.getPayload();
        assertThat(message.getHeaders()).hasSize(2);
        assertThat(message.getHeaders().get("jobId")).isEqualTo(INT_JOB_ID);
        assertThat(message.getHeaders().get("workerId")).isEqualTo("workerId");
        assertThat(jobEvent.getJobEventType()).isEqualTo(JobEventType.PROCESSING_COMPLETED);
        assertNoMoreJobUpdates();

        List<KubernetesWorkerJob> allKubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(allKubernetesWorkerJobs).hasSize(1);
        KubernetesWorkerJob kubernetesWorkerJob = allKubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(kubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(kubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.COMPLETED);
        assertThat(kubernetesWorkerJob.getStart()).isNull();
        assertThat(kubernetesWorkerJob.getEnd()).isBetween(testStartTime, OffsetDateTime.now());
    }

    @Test
    public void testDispatch_ReturnsTrue_WhenEventIsWorkflowDeleted() throws InterruptedException {

        webServer.expect().get()
                .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims?labelSelector=platform%2Fjobid%3DtheJobId&timeoutSeconds=120&watch=false")
                .andReturn(200, readAsString(BASE_TEST_PATH.resolve("get-workflows-response.json")))
                .once();
        webServer.expect().delete()
                .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/workflow-cdb67")
                .andReturn(200, "")
                .once();

        assertThat(
            legacyWorkflowEventsDispatcher.dispatch(createWorkflowEvent(K8SEventType.K8S_WORKFLOW_DELETED))
        ).isTrue();

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        RecordedRequest recordedGetPvcRequest = webServer.takeRequest();
        assertThat(recordedGetPvcRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedGetPvcRequest.getPath()).isEqualTo("/k8/api/v1/namespaces/namespace/persistentvolumeclaims?labelSelector=platform%2Fjobid%3DtheJobId&timeoutSeconds=120&watch=false");
        RecordedRequest recordedDeletePvcRequest = webServer.takeRequest();
        assertThat(recordedDeletePvcRequest.getMethod()).isEqualTo("DELETE");
        assertThat(recordedDeletePvcRequest.getPath()).isEqualTo("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/workflow-cdb67");

        assertNoMoreJobUpdates();
        assertThat(persistenceTestUtils.findAllKubernetesWorkerJob()).isEmpty();
    }

    @Test
    public void testDispatch_ReturnsTrue_WhenEventIsWorkflowFailed() throws InterruptedException {

        createWorkflowDeletionExpectations();

        assertThat(
            legacyWorkflowEventsDispatcher.dispatch(createWorkflowEvent(K8SEventType.K8S_WORKFLOW_FAILED))
        ).isTrue();

        assertThat(webServer.getRequestCount()).isEqualTo(2);
        RecordedRequest recordedGetWorkflowsRequest = webServer.takeRequest();
        assertThat(recordedGetWorkflowsRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedGetWorkflowsRequest.getPath()).isEqualTo("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3DtheJobId&watch=false");
        RecordedRequest recordedDeleteWorkflowRequest = webServer.takeRequest();
        assertThat(recordedDeleteWorkflowRequest.getMethod()).isEqualTo("DELETE");
        assertThat(recordedDeleteWorkflowRequest.getPath()).isEqualTo("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows/workflow-cdb67");

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);
        JobError jobError = (JobError) message.getPayload();
        assertThat(message.getHeaders()).hasSize(2);
        assertThat(message.getHeaders().get("jobId")).isEqualTo(INT_JOB_ID);
        assertThat(message.getHeaders().get("workerId")).isEqualTo("workerId");
        assertThat(jobError.getErrorDescription()).isEqualTo("Unknown failure");
        assertNoMoreJobUpdates();

        List<KubernetesWorkerJob> allKubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(allKubernetesWorkerJobs).hasSize(1);
        KubernetesWorkerJob kubernetesWorkerJob = allKubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJob.getJobId()).isEqualTo(JOB_ID);
        assertThat(kubernetesWorkerJob.getIntJobId()).isEqualTo(INT_JOB_ID);
        assertThat(kubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.FAILED);
        assertThat(kubernetesWorkerJob.getStart()).isNull();
        assertThat(kubernetesWorkerJob.getEnd()).isBetween(testStartTime, OffsetDateTime.now());
    }

    @Test
    public void testDispatch_ReturnsFalse_WhenEventTypeIsNotALegacyWorkflowEvent() {

        assertThat(
            legacyWorkflowEventsDispatcher.dispatch(createWorkflowEvent(K8SEventType.K8S_APP_POD_RUNNING))
        ).isFalse();

        assertThat(webServer.getRequestCount()).isZero();
        assertNoMoreJobUpdates();
        assertThat(persistenceTestUtils.findAllKubernetesWorkerJob()).isEmpty();
    }

    private K8SEvent createWorkflowEvent(K8SEventType eventType) {
        return K8SEvent.newBuilder()
                .setJobId(JOB_ID)
                .setIntJobId(INT_JOB_ID)
                .setEventType(eventType)
                .build();
    }

    private void createWorkflowDeletionExpectations() {
        webServer.expect().get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3DtheJobId&watch=false")
                .andReturn(200, readAsString(BASE_TEST_PATH.resolve("get-workflows-response.json")))
                .once();
        webServer.expect().delete()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows/workflow-cdb67")
                .andReturn(200, "")
                .once();
    }

    private void assertNoMoreJobUpdates() {
        assertThat(queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES)).isNull();
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_UPDATES)).isZero();
    }

    private void initJobUpdatesQueue() {
        queueService.sendObject(ProcessingCoreQueueNames.JOB_UPDATES, "init");
        queueService.receive(ProcessingCoreQueueNames.JOB_UPDATES);
        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_UPDATES)).isZero();
    }
}
