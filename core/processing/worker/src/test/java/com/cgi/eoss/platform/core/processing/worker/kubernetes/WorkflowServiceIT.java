package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.WorkerCoreConfig;
import com.cgi.eoss.platform.core.processing.worker.WorkerCoreTestConfig;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.Subsetting;
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

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { WorkerCoreConfig.class, WorkerCoreTestConfig.class })
@TestPropertySource(locations = { "classpath:test-worker-core-k8.properties" })
public abstract class WorkflowServiceIT {

    @Autowired
    protected WorkflowService workflowService;
    @Value("${platform.kubernetes.port}")
    protected int port;

    protected final DefaultMockServer webServer = new DefaultMockServer();
    protected static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "kubernetes");

    @Before
    public void setUp() {
        webServer.start(port);
    }

    @After
    public void shutdown() {
        webServer.shutdown();
    }

    public static class TestLegacyWorkflowWithoutConfigMapIT extends WorkflowServiceIT {

        @Test
        public void testCreateLegacyWorkflow_CreatesWorkflowWithProvidedParamValues() throws Exception {

            // Kubernetes mock response
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

            JobSpec JobSpec = createDefaultJobSpecBuilder().build();

            workflowService.createLegacyWorkflow(JobSpec);

            assertThat(webServer.getRequestCount()).isEqualTo(2);

            // get persistentVolumeClaim
            RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
            String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

            String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-storageClass.json"));
            JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

            // get workflow
            RecordedRequest workflowRecordedRequest = webServer.takeRequest();
            String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

            String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-tolerations-existingClaimName.json"));
            JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
        }

    }

    @TestPropertySource(properties = {"platform.worker.workflow.legacy.outputUploaderConfigMapName = outputUploaderConfigMapName"})
    public static class TestLegacyWorkflowWithConfigMapIT extends WorkflowServiceIT {

        @Test
        public void testCreateLegacyWorkflow_CreatesWorkflowWithProvidedParamValues() throws Exception {

            // Kubernetes mock response
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

            JobSpec JobSpec = createDefaultJobSpecBuilder().build();

            workflowService.createLegacyWorkflow(JobSpec);

            assertThat(webServer.getRequestCount()).isEqualTo(2);

            // get persistentVolumeClaim
            RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
            String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

            String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-storageClass.json"));
            JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

            // get workflow
            RecordedRequest workflowRecordedRequest = webServer.takeRequest();
            String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

            String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-tolerations-existingClaimName-configMap.json"));
            JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
        }

    }

    protected static JobSpec.Builder createDefaultJobSpecBuilder() {
        return JobSpec.newBuilder()
                .setService(
                        Service.newBuilder().setDockerImageTag("test:1.1").setId("theId")
                                .build())
                .putEnvironmentVariables("KEY1", "VALUE1")
                .setKind(Kind.WORKFLOW)
                .setJob(Job.newBuilder()
                        .setId("theJobId")
                        .setUserId("theJobOwner")
                        .setIntJobId("30")
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
                );
    }

}
