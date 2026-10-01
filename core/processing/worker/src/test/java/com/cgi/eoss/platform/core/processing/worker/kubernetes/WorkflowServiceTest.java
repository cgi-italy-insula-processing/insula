package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.cgi.eoss.platform.rpc.UserMount;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowProperties.Environment;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowProperties.Secret;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowProperties.ValueFrom;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Metadata;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Status;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.model.WorkflowInfo;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.WorkflowList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.util.Config;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;

public class WorkflowServiceTest {

    private static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "kubernetes");

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final DefaultMockServer webServer = new DefaultMockServer();

    private ApiClient apiClient;

    @Before
    public void setUp() {
        webServer.start();

        String k8Url = webServer.url("/k8").toString().replace("localhost", "127.0.0.1");

        apiClient = Config.fromUrl(k8Url);
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder().readTimeout(1, TimeUnit.SECONDS).build());
    }

    @After
    public void shutdown() {
        webServer.shutdown();
    }

    @Test
    public void testCreateLegacyWorkflow() throws Exception {

        // Kubernetes mock response
        {
            // persistent volume claim response
            webServer.expect()
                        .post()
                        .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims")
                        .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata()
                                    .withName("ExistingClaimName").endMetadata().build())
                        .once();

            webServer.expect()
                    .get()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/bps-user-mounts-pvc")
                    .andReturn(200, "")
                    .once();

            // workflow creation response
            webServer.expect()
                    .post()
                    .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?pretty=false")
                    .andReturn(200, "workflows")
                    .once();
        }

        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();
        legacyWorkflowProperties.setPersistentVolumeClaimStorageClass(null);

        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);
        UserMount userMount = UserMount.newBuilder().
                setMountPath("mountPath")
                .setType("ro")
                .setName("bps")
                .setTargetPath("/target")
                .build();
        JobSpec jobSpecWithUserMounts = createDefaultJobSpecBuilder().addAllUserMount(Collections.singletonList(userMount)).build();
        workflowService.createLegacyWorkflow(jobSpecWithUserMounts);

        assertThat(webServer.getRequestCount()).isEqualTo(3);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestUserMountPersistentVolumeClaim = webServer.takeRequest();
        assertThat(recordedRequestUserMountPersistentVolumeClaim.getBody().readUtf8()).isEqualTo("");

        // get workflow
        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-userMountsClaimName-defaultAction.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson,  new CustomComparator(JSONCompareMode.STRICT,
                new Customization("spec.templates[1].container.env[3].value",  new RegularExpressionValueMatcher<>("theJobOwner"))
        ));
    }

    @Test
    public void testCreateLegacyWorkflow_ThrowsIOException_WhenUserMountPVCIsNotFound() throws Exception {

        // Kubernetes mock response
        {
            // persistent volume claim response
            webServer.expect()
                    .post()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims")
                    .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata()
                            .withName("ExistingClaimName").endMetadata().build())
                    .once();

            webServer.expect()
                    .get()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/bps-user-mounts-pvc")
                    .andReturn(404, "")
                    .once();
        }

        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();
        legacyWorkflowProperties.setPersistentVolumeClaimStorageClass(null);

        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);
        UserMount userMount = UserMount.newBuilder().
                setMountPath("mountPath")
                .setType("ro")
                .setName("bps")
                .setTargetPath("/target")
                .build();
        JobSpec jobSpecWithUserMounts = createDefaultJobSpecBuilder().addAllUserMount(Collections.singletonList(userMount)).build();
        try {
            workflowService.createLegacyWorkflow(jobSpecWithUserMounts);
            fail();
        } catch (IOException exception) {
            assertThat(exception.getMessage()).isEqualTo("PVC 'bps-user-mounts-pvc' not found. Failing job.");
        }

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestUserMountPersistentVolumeClaim = webServer.takeRequest();
        assertThat(recordedRequestUserMountPersistentVolumeClaim.getBody().readUtf8()).isEqualTo("");
    }

    @Test
    public void testCreateLegacyWorkflow_ThrowsIOException_WhenGetUserMountPVCFails() throws Exception {

        // Kubernetes mock response
        {
            // persistent volume claim response
            webServer.expect()
                    .post()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims")
                    .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata()
                            .withName("ExistingClaimName").endMetadata().build())
                    .once();

            webServer.expect()
                    .get()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/bps-user-mounts-pvc")
                    .andReturn(400, "")
                    .once();
        }

        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();
        legacyWorkflowProperties.setPersistentVolumeClaimStorageClass(null);

        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);
        UserMount userMount = UserMount.newBuilder().
                setMountPath("mountPath")
                .setType("ro")
                .setName("bps")
                .setTargetPath("/target")
                .build();
        JobSpec jobSpecWithUserMounts = createDefaultJobSpecBuilder().addAllUserMount(Collections.singletonList(userMount)).build();
        try {
            workflowService.createLegacyWorkflow(jobSpecWithUserMounts);
            fail();
        } catch (IOException exception) {
            assertThat(exception.getMessage()).contains("Error checking PVC 'bps-user-mounts-pvc'");
        }

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestUserMountPersistentVolumeClaim = webServer.takeRequest();
        assertThat(recordedRequestUserMountPersistentVolumeClaim.getBody().readUtf8()).isEqualTo("");
    }

    @Test
    public void testCreateLegacyWorkflowWithJobResourceRequestAndStorageClassSet() throws Exception {

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

        JobSpec jobSpec = createDefaultJobSpecBuilder()
                .setResourceRequest(ResourceRequest.newBuilder().setCpus("2")
                        .setRam("3Mi")
                        .setLimits(ResourceSpec.newBuilder().setCpu("3").setRam("4Mi").build())
                        .setStorage(50).setGpus("1"))
                .build();
        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();

        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);
        workflowService.createLegacyWorkflow(jobSpec);

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        // get persistentVolumeClaims
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-resourcesRequests-storageClass.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get workflow
        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-jobResourcesRequests.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testCreateLegacyWorkflowPVCStorageClassNotSetAndTolerationsAndEnvVarsFromSecretsSet() throws Exception {

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

        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();
        legacyWorkflowProperties.setPersistentVolumeClaimStorageClass(null);
        legacyWorkflowProperties.getInputDownloader().getEnvs().add(createEnvVarsFromSecret("lg_indw_name_0", "lg_indw_secret_name_0", "lg_indw_secret_key_0"));
        legacyWorkflowProperties.getInputDownloader().getEnvs().add(createEnvVarsFromSecret("lg_indw_name_1", "lg_indw_secret_name_1", "lg_indw_secret_key_1"));
        legacyWorkflowProperties.getProcessing().getEnvs().add(createEnvVarsFromSecret("lg_pr_name_0", "lg_pr_secret_name_0", "lg_pr_secret_key_0"));
        legacyWorkflowProperties.getOutputUploader().getEnvs().add(createEnvVarsFromSecret("lg_op_name_0", "lg_op_secret_name_0", "lg_op_secret_key_0"));

        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);

        workflowService.createLegacyWorkflow(
                createDefaultJobSpecBuilder()
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
                        )
                        .build()

        );

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get workflow
        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-tolerations-existingClaimName.json"));

        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testCreateLegacyWorkflowWithJobResourceRequestSet() throws Exception {

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

        JobSpec jobSpec = createDefaultJobSpecBuilder()
                .setResourceRequest(ResourceRequest.newBuilder().setCpus("2")
                        .setRam("3Mi")
                        .setLimits(ResourceSpec.newBuilder().setCpu("3").setRam("4Mi").build())
                        .setStorage(50)
                        .setGpus("1")).build();

        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();
        legacyWorkflowProperties.setPersistentVolumeClaimStorageClass(null);

        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);

        workflowService.createLegacyWorkflow(jobSpec);

        assertThat(webServer.getRequestCount()).isEqualTo(2);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-resourcesRequests.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get workflow
        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-jobResourcesRequests.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testCreateLegacyWorkflowWithPVCStorageClass() throws Exception {

        // Kubernetes mock response
        {
            // persistent volume claim response
            webServer.expect()
                        .post()
                        .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims")
                        .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata()
                                    .withName("ExistingClaimName").endMetadata().build())
                        .once();
            // get usermount pvc
            webServer.expect()
                    .get()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/bps-user-mounts-pvc")
                    .andReturn(200, "")
                    .once();
            // workflow creation response
            webServer.expect()
                        .post()
                        .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?pretty=false")
                        .andReturn(200, "workflows")
                        .once();
        }

        WorkflowService workflowService = createWorkflowService(createDefaultLegacyWorkflowProperties());

        UserMount userMount = UserMount.newBuilder().
                setMountPath("mountPath")
                .setType("ro")
                .setName("bps")
                .setTargetPath("/target")
                .build();
        JobSpec jobSpecWithUserMounts = createDefaultJobSpecBuilder().addAllUserMount(Collections.singletonList(userMount)).build();
        workflowService.createLegacyWorkflow(jobSpecWithUserMounts);

        assertThat(webServer.getRequestCount()).isEqualTo(3);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();

        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-storageClass.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);

        // get persistentVolumeClaim
        RecordedRequest recordedRequestUserMountPersistentVolumeClaim = webServer.takeRequest();
        assertThat(recordedRequestUserMountPersistentVolumeClaim.getBody().readUtf8()).isEqualTo("");

        // get workflow
        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();

        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-userMountsClaimName-defaultAction.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson,  new CustomComparator(JSONCompareMode.STRICT,
                new Customization("spec.templates[1].container.env[3].value",  new RegularExpressionValueMatcher<>("theJobOwner"))
        ));
    }

    @Test
    public void testDeleteWorkflowForJob() throws Exception {

        final String jobId = "10";

        // Kubernetes mock response
        {
            // list workflows response
            {
                WorkflowList workflowList = new WorkflowList();
                {
                    Workflow workflow = new Workflow();
                    Metadata metadata = new Metadata();
                    metadata.setName("workflow-one");
                    workflow.setMetadata(metadata);
                    workflowList.getItems().add(workflow);
                }
                {
                    Workflow workflow = new Workflow();
                    Metadata metadata = new Metadata();
                    metadata.setName("workflow-two");
                    workflow.setMetadata(metadata);
                    workflowList.getItems().add(workflow);
                }

                webServer.expect()
                            .get()
                            .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3D" + jobId + "&watch=false")
                            .andReturn(200, workflowList)
                            .once();
            }

            // delete workflow one response
            webServer.expect()
                        .delete()
                        .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows/workflow-one")
                        .andReturn(200, "OK")
                        .once();

            // delete workflow two response
            webServer.expect()
                        .delete()
                        .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows/workflow-two")
                        .andReturn(200, "OK")
                        .once();
        }

        WorkflowService workflowService = createWorkflowService(createDefaultLegacyWorkflowProperties());

        workflowService.deleteWorkflowForJob(jobId);

        assertThat(webServer.getRequestCount()).isEqualTo(3);

        // get workflows
        RecordedRequest recordedRequestListWorkflows = webServer.takeRequest();
        assertThat(recordedRequestListWorkflows.getBody().readUtf8()).isEmpty();

        // delete workflow one
        RecordedRequest recordedRequestDeleteWorkflowOne = webServer.takeRequest();
        assertThat(recordedRequestDeleteWorkflowOne.getBody().readUtf8()).isEqualTo("{}");

        // delete workflow two
        RecordedRequest recordedRequestDeleteWorkflowTwo = webServer.takeRequest();
        assertThat(recordedRequestDeleteWorkflowTwo.getBody().readUtf8()).isEqualTo("{}");
    }

    @Test
    public void testCleanUpWorkflowForJob() throws Exception {

        final String jobId = "10";

        // Kubernetes mock response
        {
            // list persistent volume claims response
            {
                V1PersistentVolumeClaimList pvcList = new V1PersistentVolumeClaimList();
                {
                    V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaim();
                    V1ObjectMeta metadata = new V1ObjectMeta();
                    metadata.setName("pvc-one");
                    pvc.metadata(metadata);
                    pvcList.getItems().add(pvc);
                }
                {
                    V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaim();
                    V1ObjectMeta metadata = new V1ObjectMeta();
                    metadata.setName("pvc-two");
                    pvc.metadata(metadata);
                    pvcList.getItems().add(pvc);
                }

                webServer.expect()
                            .get()
                            .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims?labelSelector=platform%2Fjobid%3D" + jobId + "&timeoutSeconds=120&watch=false")
                            .andReturn(200, pvcList)
                            .once();
            }

            // delete pvc one response
            webServer.expect()
                        .delete()
                        .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/pvc-one")
                        .andReturn(200, "{}")
                        .once();

            // delete pvc two response
            webServer.expect()
                        .delete()
                        .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/pvc-two")
                        .andReturn(200, "{}")
                        .once();
        }

        WorkflowService workflowService = createWorkflowService(createDefaultLegacyWorkflowProperties());

        workflowService.cleanUpWorkflowForJob(jobId);

        assertThat(webServer.getRequestCount()).isEqualTo(3);

        // get persistent volume claims
        RecordedRequest recordedRequestListPVCs = webServer.takeRequest();
        assertThat(recordedRequestListPVCs.getBody().readUtf8()).isEmpty();

        // delete persistent volume claim one
        RecordedRequest recordedRequestDeletePvcOne = webServer.takeRequest();
        assertThat(recordedRequestDeletePvcOne.getBody().readUtf8()).isEqualTo("{}");

        // delete persistent volume claim two
        RecordedRequest recordedRequestDeletePvcTwo = webServer.takeRequest();
        assertThat(recordedRequestDeletePvcTwo.getBody().readUtf8()).isEqualTo("{}");
    }

    @Test
    public void testCreateLegacyWorkflow_CreatesWorkflowWithConfigMapVolumeAndVolumeMounts_WhenConfigMapNameIsSet() throws Exception {

        {
            webServer.expect()
                    .post()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims")
                    .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata()
                            .withName("ExistingClaimName").endMetadata().build())
                    .once();

            webServer.expect()
                    .get()
                    .withPath("/k8/api/v1/namespaces/namespace/persistentvolumeclaims/bps-user-mounts-pvc")
                    .andReturn(200, "")
                    .once();

            webServer.expect()
                    .post()
                    .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?pretty=false")
                    .andReturn(200, "workflows")
                    .once();
        }

        LegacyWorkflowProperties legacyWorkflowProperties = createDefaultLegacyWorkflowProperties();
        legacyWorkflowProperties.setPersistentVolumeClaimStorageClass(null);
        legacyWorkflowProperties.setOutputUploaderConfigMapName("outputUploaderConfigMapName");
        WorkflowService workflowService = createWorkflowService(legacyWorkflowProperties);
        UserMount userMount = UserMount.newBuilder().
                setMountPath("mountPath")
                .setType("ro")
                .setName("bps")
                .setTargetPath("/target")
                .build();
        JobSpec jobSpecWithUserMounts = createDefaultJobSpecBuilder().addAllUserMount(Collections.singletonList(userMount)).build();

        workflowService.createLegacyWorkflow(jobSpecWithUserMounts);

        assertThat(webServer.getRequestCount()).isEqualTo(3);
        RecordedRequest recordedRequestPersistentVolumeClaim = webServer.takeRequest();
        String actualPersistentVolumeClaimJson = recordedRequestPersistentVolumeClaim.getBody().readUtf8();
        String expectedPersistentVolumeClaimJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim.json"));
        JSONAssert.assertEquals(expectedPersistentVolumeClaimJson, actualPersistentVolumeClaimJson, JSONCompareMode.STRICT);
        RecordedRequest recordedRequestUserMountPersistentVolumeClaim = webServer.takeRequest();
        assertThat(recordedRequestUserMountPersistentVolumeClaim.getBody().readUtf8()).isEqualTo("");
        RecordedRequest workflowRecordedRequest = webServer.takeRequest();
        String actualWorkflowJson = workflowRecordedRequest.getBody().readUtf8();
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-userMountsClaimName-configMap-defaultAction.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson,  new CustomComparator(JSONCompareMode.STRICT,
                new Customization("spec.templates[1].container.env[3].value",  new RegularExpressionValueMatcher<>("theJobOwner"))
        ));
    }

    @Test
    public void testGetWorkflowInfo_RetrievesWorkflowInfo_WhenOnlyOneWorkflowExistForGivenJobId() throws Exception {
        Workflow workflow = new Workflow();

        Metadata metadata = new Metadata();
        metadata.setName("workflowName");
        workflow.setMetadata(metadata);

        Status stuckStartingWorkerJobWorkflowStatus = new Status();
        stuckStartingWorkerJobWorkflowStatus.setStartedAt(Instant.parse("2026-06-12T14:00:10.001Z"));
        stuckStartingWorkerJobWorkflowStatus.setFinishedAt(Instant.parse("2026-06-12T14:00:12.001Z"));
        stuckStartingWorkerJobWorkflowStatus.setPhase("Succeeded");
        workflow.setStatus(stuckStartingWorkerJobWorkflowStatus);

        WorkflowList workflowList = new WorkflowList();
        workflowList.getItems().add(workflow);

        String jobId = "jobId";

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3D" + jobId + "&watch=false")
                .andReturn(200, objectMapper.writeValueAsString(workflowList))
                .once();

        WorkflowInfo expectedWorkflowInfo = new WorkflowInfo();
        expectedWorkflowInfo.setStatus("Succeeded");
        expectedWorkflowInfo.setStartedAt(Instant.parse("2026-06-12T14:00:10.001Z"));
        expectedWorkflowInfo.setFinishedAt(Instant.parse("2026-06-12T14:00:12.001Z"));

        assertThat(createWorkflowService(createDefaultLegacyWorkflowProperties()).getWorkflowInfo(jobId))
                .containsExactly(expectedWorkflowInfo);
    }

    @Test
    public void testGetWorkflowInfo_RetrievesWorkflowInfo_WhenMoreThenOneWorkflowExistForGivenJobId() throws Exception {
        Workflow olderWorkflow = new Workflow();
        Metadata olderWorkflowMetadata = new Metadata();
        olderWorkflowMetadata.setName("workflowName");
        olderWorkflow.setMetadata(olderWorkflowMetadata);
        Status olderWorkflowStatus = new Status();
        olderWorkflowStatus.setStartedAt(Instant.parse("2026-06-12T14:00:10.001Z"));
        olderWorkflowStatus.setFinishedAt(Instant.parse("2026-06-12T14:00:20.001Z"));
        olderWorkflowStatus.setPhase("Succeeded");
        olderWorkflow.setStatus(olderWorkflowStatus);

        Workflow latestWorkflow = new Workflow();
        Metadata latestWorkflowMetadata = new Metadata();
        latestWorkflowMetadata.setName("workflowName");
        latestWorkflow.setMetadata(latestWorkflowMetadata);
        Status latestWorkflowStatus = new Status();
        latestWorkflowStatus.setStartedAt(Instant.parse("2026-06-12T14:00:30.001Z"));
        latestWorkflowStatus.setFinishedAt(Instant.parse("2026-06-12T14:00:40.001Z"));
        latestWorkflowStatus.setPhase("Succeeded");
        latestWorkflow.setStatus(latestWorkflowStatus);

        Workflow noStatusWorkflow = new Workflow();
        Metadata noStatusWorkflowMetadata = new Metadata();
        noStatusWorkflowMetadata.setName("workflowName");
        noStatusWorkflow.setMetadata(noStatusWorkflowMetadata);
        noStatusWorkflow.setStatus(null);

        WorkflowList workflowList = new WorkflowList();
        workflowList.getItems().add(olderWorkflow);
        workflowList.getItems().add(latestWorkflow);
        workflowList.getItems().add(noStatusWorkflow);

        String jobId = "jobId";

        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3D" + jobId + "&watch=false")
                .andReturn(200, objectMapper.writeValueAsString(workflowList))
                .once();

        WorkflowInfo expectedOlderWorkflowInfo = new WorkflowInfo();
        expectedOlderWorkflowInfo.setStatus("Succeeded");
        expectedOlderWorkflowInfo.setStartedAt(Instant.parse("2026-06-12T14:00:10.001Z"));
        expectedOlderWorkflowInfo.setFinishedAt(Instant.parse("2026-06-12T14:00:20.001Z"));
        WorkflowInfo expectedLatestWorkflowInfo = new WorkflowInfo();
        expectedLatestWorkflowInfo.setStatus("Succeeded");
        expectedLatestWorkflowInfo.setStartedAt(Instant.parse("2026-06-12T14:00:30.001Z"));
        expectedLatestWorkflowInfo.setFinishedAt(Instant.parse("2026-06-12T14:00:40.001Z"));
        WorkflowInfo expectedNoStatusWorkflowInfo = new WorkflowInfo();

        assertThat(createWorkflowService(createDefaultLegacyWorkflowProperties()).getWorkflowInfo(jobId))
                .containsExactlyInAnyOrder(expectedOlderWorkflowInfo, expectedLatestWorkflowInfo, expectedNoStatusWorkflowInfo);
    }

    @Test
    public void testGetWorkflowInfo_ReturnsEmptyList_WhenNoWorkflowIsRetrieved() throws Exception {

        String jobId = "jobId";
        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3D" + jobId + "&watch=false")
                .andReturn(200, objectMapper.writeValueAsString(new WorkflowList()))
                .once();

        assertThat(createWorkflowService(createDefaultLegacyWorkflowProperties()).getWorkflowInfo(jobId)).isEmpty();
    }

    @Test
    public void testGetWorkflowInfo_ThrowsIOException_WhenResponseIsMalformed() {

        String jobId = "jobId";
        webServer.expect()
                .get()
                .withPath("/k8/apis/argoproj.io/v1alpha1/namespaces/namespace/workflows?labelSelector=platform%2Fjobid%3D" + jobId + "&watch=false")
                .andReturn(200, "SomeStringResponse")
                .once();

        assertThatThrownBy(() -> createWorkflowService(createDefaultLegacyWorkflowProperties()).getWorkflowInfo(jobId))
                .isInstanceOf(IOException.class)
                .hasMessage("An exception occurred reading api response for jobId: jobId");
    }

    private WorkflowService createWorkflowService(LegacyWorkflowProperties legacyWorkflowProperties) {
        ExpressionParser expressionParser = new SpelExpressionParser();

        return new WorkflowService(
                new CoreV1Api(apiClient),
                new CustomObjectsApi(apiClient),
                "namespace",
                new CoreLegacyWorkflowBuilder(legacyWorkflowProperties),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private JobSpec.Builder createDefaultJobSpecBuilder() {
        return JobSpec.newBuilder()
                    .setService(Service.newBuilder().setDockerImageTag("test:1.1").setId("theId").build())
                    .putEnvironmentVariables("KEY1", "VALUE1")
                    .setKind(Kind.WORKFLOW)
                    .setJob(Job.newBuilder()
                                .setId("theJobId")
                                .setUserId("theJobOwner")
                                .setIntJobId("30")
                                .build());
    }

    private static LegacyWorkflowProperties createDefaultLegacyWorkflowProperties() {
        LegacyWorkflowProperties workflowProperties = new LegacyWorkflowProperties();
        workflowProperties.setExternalRegistryImagePullSecretName("externalRegistryImagePullSecretName");
        workflowProperties.getInputDownloader().setImageName("inputDownloaderImageName");
        workflowProperties.setInternalPlatformRegistryImagePullSecretName("internalPlatformRegistryImagePullSecretName");
        workflowProperties.setInternalPlatformRegistryPrefix("internalPlatformRegistryPrefix");
        workflowProperties.setNamespace("namespace");
        workflowProperties.getOutputUploader().setImageName("outputUploaderImageName");
        workflowProperties.setPersistentVolumeClaimStorageClass("persistentVolumeClaimStorageClass");

        return workflowProperties;
    }

    private static Environment createEnvVarsFromSecret(String envName, String secretName, String secretKey) {
        return new Environment(envName, new ValueFrom(new Secret(secretName, secretKey)));
    }
}
