package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.WorkerCoreConfig;
import com.cgi.eoss.platform.core.processing.worker.WorkerCoreTestConfig;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service.KubernetesWorkerJobDataService;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.testutils.PersistenceTestUtils;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.ListMeta;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Metadata;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Spec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Status;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.WorkflowList;
import com.cgi.eoss.platform.core.queues.service.Message;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.K8SEvent;
import com.cgi.eoss.platform.rpc.K8SEventType;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.StopJob;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.mockwebserver.DefaultMockServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.jms.JMSException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {WorkerCoreConfig.class, WorkerCoreTestConfig.class})
@TestPropertySource(locations = {"classpath:test-worker-core-k8.properties"})
public class KubernetesCoreDispatcherIT {

    @Autowired
    private QueueService queueService;

    @Autowired
    private KubernetesCoreDispatcher kubernetesCoreDispatcher;

    @Autowired
    private PersistenceTestUtils persistenceTestUtils;

    @Autowired
    private KubernetesWorkerJobDataService kubernetesWorkerJobDataService;

    private final DefaultMockServer webServer = new DefaultMockServer();
    protected static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "kubernetes");


    @Value("${platform.kubernetes.port}")
    private int port;

    @Before
    public void setUp() {
        persistenceTestUtils.cleanDatabase();
        persistenceTestUtils.assertDbIsEmpty();

        webServer.start(port);
    }

    @After
    public void shutdown() {
        webServer.shutdown();

        persistenceTestUtils.cleanDatabase();
        persistenceTestUtils.assertDbIsEmpty();
    }

    @Test
    public void testReceiveJobSpec_DispatchesWorkflowStartRequest_WhenJobSpecKindIsWorkflow() throws Exception {

        {
            // persistent volume claim response
            webServer.expect()
                .post()
                .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims")
                .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata()
                    .withName("persistentvolumeclaim1").endMetadata().build())
                .once();

            // workflow creation response
            webServer.expect()
                .post()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?pretty=false")
                .andReturn(200, "workflows")
                .once();
        }

        JobSpec jobSpec = JobSpec.newBuilder()
            .setService(
                Service.newBuilder().setDockerImageTag("test:1.1").setId("theId")
                    .build())
            .putEnvironmentVariables("KEY1", "VALUE1")
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                .setId("theJobId")
                .setIntJobId("30")
                .setUserId("theJobOwner")
                .build())
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .setSubsetting(Subsetting.newBuilder()
                        .setAoi("aoi").setFormat("format")
                        .build())
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .build()
            ).build();

        kubernetesCoreDispatcher.receiveJobSpec(jobSpec);

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-storageClass.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-tolerations-existingClaimName.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_UPDATES)).isEqualTo(0);

        assertThat(message.getHeaders().size()).isEqualTo(2);
        assertThat(message.getHeaders().get("workerId")).isEqualTo("workerId");
        assertThat(message.getHeaders().get("jobId")).isEqualTo("30");

        JobEvent actualJobEvent = (JobEvent) message.getPayload();
        assertThat(actualJobEvent.getJobEventPayload()).isEqualTo("");
        assertThat(actualJobEvent.getJobEventType()).isEqualTo(JobEventType.DATA_FETCHING_STARTED);

        List<KubernetesWorkerJob> kubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(kubernetesWorkerJobs).hasSize(1);

        KubernetesWorkerJob kubernetesWorkerJob = kubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJob.getJobId()).isEqualTo("theJobId");
        assertThat(kubernetesWorkerJob.getIntJobId()).isEqualTo("30");
        assertThat(kubernetesWorkerJob.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTING);
        assertThat(kubernetesWorkerJob.getStart()).isNotNull();

    }

    @Test
    public void testReceiveStopJobRequest_DispatchesWorkflowStopRequest_WhenRequestKindIsWorkflow() throws JMSException {

        {
            webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3DtheJobId&watch=false")
                .andReturn(200, createworkflowList())
                .once();

            webServer.expect()
                .delete()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows/workflow1")
                .andReturn(200, "workflows")
                .once();
        }

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("theJobId");
        kubernetesWorkerJob.setIntJobId("30");
        kubernetesWorkerJob.setStart(OffsetDateTime.now());
        kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.STARTING);
        kubernetesWorkerJob = kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        StopJob stopJob = StopJob.newBuilder()
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                .setId(kubernetesWorkerJob.getJobId())
                .setIntJobId(kubernetesWorkerJob.getIntJobId())
                .setServiceId("service-id")
                .build())
            .build();

        kubernetesCoreDispatcher.receiveStopJobRequest(stopJob);

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);

        assertThat(message.getHeaders().size()).isEqualTo(2);
        assertThat(message.getHeaders().get("workerId")).isEqualTo("workerId");
        assertThat(message.getHeaders().get("jobId")).isEqualTo("30");

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_UPDATES)).isEqualTo(0);

        List<KubernetesWorkerJob> kubernetesWorkerJobs = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(kubernetesWorkerJobs).hasSize(1);

        KubernetesWorkerJob kubernetesWorkerJobRetrived = kubernetesWorkerJobs.get(0);
        assertThat(kubernetesWorkerJobRetrived.getJobId()).isEqualTo("theJobId");
        assertThat(kubernetesWorkerJobRetrived.getIntJobId()).isEqualTo("30");
        assertThat(kubernetesWorkerJobRetrived.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STOPPED);
        assertThat(kubernetesWorkerJobRetrived.getStart()).isNotNull();

    }

    @Test
    public void testReceiveKubernetesEvent_DispatchesTheEventRequested() throws JMSException, InterruptedException {

        OffsetDateTime jobStarted = OffsetDateTime.now();

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("theJobId");
        kubernetesWorkerJob.setIntJobId("30");
        kubernetesWorkerJob.setStart(jobStarted);
        kubernetesWorkerJob.setStatus(KubernetesWorkerJob.Status.ERROR);
        kubernetesWorkerJob = kubernetesWorkerJobDataService.save(kubernetesWorkerJob);

        kubernetesCoreDispatcher.receiveKubernetesEvent(K8SEvent.newBuilder()
            .setEventType(K8SEventType.K8S_WORKFLOW_RUNNING)
            .setJobId("theJobId")
            .setIntJobId("30")
            .build());

        List<KubernetesWorkerJob> allKubernetesWorkerJob = persistenceTestUtils.findAllKubernetesWorkerJob();
        assertThat(allKubernetesWorkerJob.size()).isEqualTo(1);

        KubernetesWorkerJob workerJobFromDb = allKubernetesWorkerJob.get(0);

        assertThat(workerJobFromDb.getJobId()).isEqualTo("theJobId");
        assertThat(workerJobFromDb.getIntJobId()).isEqualTo("30");
        assertThat(workerJobFromDb.getStatus()).isEqualTo(KubernetesWorkerJob.Status.STARTED);
        assertThat(workerJobFromDb.getStart()).isEqualTo(jobStarted);

        Message message = queueService.receiveNoWait(ProcessingCoreQueueNames.JOB_UPDATES);
        assertThat(message.getHeaders().size()).isEqualTo(2);
        assertThat(message.getHeaders().get("workerId")).isEqualTo("workerId");
        assertThat(message.getHeaders().get("jobId")).isEqualTo("30");

        assertThat(queueService.getQueueLength(ProcessingCoreQueueNames.JOB_UPDATES)).isEqualTo(0);
    }

    private WorkflowList createworkflowList() {

        Metadata metadata = new Metadata();
        metadata.setName("workflow1");

        Workflow workflow = new Workflow();
        workflow.setMetadata(metadata);
        workflow.getMetadata().setName("workflow1");
        workflow.setSpec(new Spec());

        Status status = new Status();
        status.setPhase("RUNNING");

        workflow.setStatus(status);

        WorkflowList workflowList = new WorkflowList();
        List<Workflow> workflows = new ArrayList<>();
        workflows.add(workflow);
        workflowList.setItems(workflows);

        ListMeta listMeta = new ListMeta();
        listMeta.setResourceVersion("1");
        workflowList.setMetadata(listMeta);

        return workflowList;
    }
}
