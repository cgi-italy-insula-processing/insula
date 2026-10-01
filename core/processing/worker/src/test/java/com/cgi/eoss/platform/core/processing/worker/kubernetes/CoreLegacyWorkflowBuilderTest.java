package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.SharedMemory;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.cgi.eoss.platform.rpc.UserMount;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Workflow;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;

import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;

public class CoreLegacyWorkflowBuilderTest {

    private static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "kubernetes");

    private static final Gson GSON = new Gson();

    private static final CustomComparator WORKFLOW_PARAMETERS_COMPARATOR = new CustomComparator(JSONCompareMode.STRICT,
        regexMatcher("spec.templates[0].steps[0][0].arguments.parameters[0].value")
    );

    @Test
    public void testGetLegacyWorkflowWhenExistingClaimNameIsSet() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(createDefaultJobSpecBuilder().build(), "ExistingClaimName");

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflowWhenExistingClaimNameAndJobResourceRequestsAreSet() throws Exception {

        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .setResourceRequest(ResourceRequest.newBuilder().setCpus("2").setRam("3Mi")
                .setLimits(ResourceSpec.newBuilder().setRam("4Mi").setCpu("3").build()).setStorage(50).setGpus("1")).build();

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, "persistentvolumeclaim1");

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-jobResourcesRequests.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_SetsJobOwnerAsUUIDInInputEnv_WhenJobContainsUUID() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());
        UserMount userMount = UserMount.newBuilder().
            setMountPath("mountPath")
            .setType("ro")
            .setName("bps")
            .setTargetPath("/target")
            .build();
        JobSpec jobSpecWithUUIDAndUserMounts = createDefaultJobSpecWithUUIDBuilder().addAllUserMount(Collections.singletonList(userMount)).build();
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpecWithUUIDAndUserMounts, "ExistingClaimName");

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-existingClaimName-userMountsClaimName.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, new CustomComparator(JSONCompareMode.STRICT,
            new Customization("spec.templates[1].container.env[3].value", new RegularExpressionValueMatcher<>("UserUUID"))
        ));
    }

    @Test
    public void testGetLegacyWorkflow_ReturnsWorkflow_WhenExistingClaimNameAndUserMountsClaimNameAreNotSet() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());
        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .build()
            ).build();
        String existingClaimName = null;
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, WORKFLOW_PARAMETERS_COMPARATOR);

        DocumentContext workflowResult = JsonPath.parse(actualWorkflowJson);
        String expectedParams = "{\"ParamB\":{\"type\":\"URL\",\"values\":[\"ParamBValue1\"],\"subsetting\":null},\"ParamA\":{\"type\":\"OTHER\",\"values\":[\"ParamAValue1\"],\"subsetting\":null}}";
        assertThat(workflowResult.<String>read("spec.templates[0].steps[0][0].arguments.parameters[0].value"))
            .isEqualTo(expectedParams);
    }

    @Test
    public void testGetLegacyWorkflow_WorkflowWithInputsWithSubsettingIsReturned_WhenJobParamWithSubsettingIsProvided() throws JSONException {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());
        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .setSubsetting(Subsetting.newBuilder()
                        .setAoi("aoi").setFormat("format").build())
                    .build()
            ).build();

        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, WORKFLOW_PARAMETERS_COMPARATOR);

        DocumentContext workflowResult = JsonPath.parse(actualWorkflowJson);
        String expectedParams = "{\"ParamB\":{\"type\":\"URL\",\"values\":[\"ParamBValue1\"],\"subsetting\":{\"aoi\":\"aoi\",\"format\":\"format\"}},\"ParamA\":{\"type\":\"OTHER\",\"values\":[\"ParamAValue1\"],\"subsetting\":null}}";
        assertThat(workflowResult.<String>read("spec.templates[0].steps[0][0].arguments.parameters[0].value"))
            .isEqualTo(expectedParams);
    }

    @Test
    public void testGetLegacyWorkflowWhenJobResourceRequestIsSet() throws Exception {

        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .setResourceRequest(ResourceRequest.newBuilder().setCpus("2").setRam("3Mi")
                .setLimits(ResourceSpec.newBuilder().setCpu("3").setRam("4Mi").build()).setStorage(50).setGpus("1"))
            .build();

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());

        String existingClaimName = null;
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);
        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-jobResourcesRequests.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_ReturnsWorkflowWithUserMountsSetAsVolume_WhenUserBindsAreSet() throws Exception {

        JobSpec jobSpec = createDefaultJobSpecWithUserMountClaim()
            .build();

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());
        String existingClaimName = null;
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);

        String actualWorkflowJson = GSON.toJson(workflow);

        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-userBinds.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testCreatePVC() throws Exception {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setPersistentVolumeClaimStorageClass(null);
        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);

        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .build();

        V1PersistentVolumeClaim pvc = coreLegacyWorkflowBuilder.getPersistentVolumeClaim(jobSpec);

        String actualPvcJson = GSON.toJson(pvc);
        String expectedPvcJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim.json"));
        JSONAssert.assertEquals(expectedPvcJson, actualPvcJson, JSONCompareMode.STRICT);

    }

    @Test
    public void testCreatePVCWithResourceRequest() throws Exception {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setPersistentVolumeClaimStorageClass(null);

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);

        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .setResourceRequest(ResourceRequest.newBuilder().setCpus("2").setRam("50Gi").setStorage(50).setGpus("1"))
            .build();

        V1PersistentVolumeClaim pvc = coreLegacyWorkflowBuilder.getPersistentVolumeClaim(jobSpec);

        String actualPvcJson = GSON.toJson(pvc);
        String expectedPvcJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-resourcesRequests.json"));
        JSONAssert.assertEquals(expectedPvcJson, actualPvcJson, JSONCompareMode.STRICT);

    }

    @Test
    public void testCreatePVCWithResourceRequestAndStorageClass() throws Exception {
        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());

        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .setResourceRequest(ResourceRequest.newBuilder().setCpus("2").setRam("50Gi").setStorage(50).setGpus("1"))
            .build();

        V1PersistentVolumeClaim pvc = coreLegacyWorkflowBuilder.getPersistentVolumeClaim(jobSpec);

        String actualPvcJson = GSON.toJson(pvc);
        String expectedPvcJson = readAsString(BASE_TEST_PATH.resolve("persistent-volume-claim-with-resourcesRequests-storageClass.json"));
        JSONAssert.assertEquals(expectedPvcJson, actualPvcJson, JSONCompareMode.STRICT);

    }

    @Test
    public void testGetLegacyWorkflowLabelSelector() {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());

        assertThat(coreLegacyWorkflowBuilder.getLabelSelector("10")).isEqualTo("platform/jobid=10");
    }

    @Test
    public void testGetLegacyWorkflowW_WhenExistingEnvironmentsAreSet() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(addEnvironment(createDefaultLegacyWorkflowProperties()));

        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(createDefaultJobSpecBuilder().build(), null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-env-from-secrets.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_HaveSecretEnvironmentsAndSkipsEnvironmentsWithValueFromSetNull_WhenExistingEnvironmentsAreSet() throws Exception {

        LegacyWorkflowProperties workflowProperties = addEnvironment(createDefaultLegacyWorkflowProperties());

        LegacyWorkflowProperties.Environment environmentToSkip = new LegacyWorkflowProperties.Environment();
        environmentToSkip.setName("environmentToSkip");

        workflowProperties.getInputDownloader().getEnvs().add(environmentToSkip);
        workflowProperties.getProcessing().getEnvs().add(environmentToSkip);
        workflowProperties.getOutputUploader().getEnvs().add(environmentToSkip);

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(createDefaultJobSpecBuilder().build(), null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-env-from-secrets.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_HasCWLRelatedParameters_WhenDescriptorTypeIsCWL() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());

        JobSpec jobSpec = createDefaultJobSpecBuilderWithDockerCommandsAndArguments()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .setInputBinding(InputBinding.newBuilder().setPosition(0).build())
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamC")
                    .addParamValue("ParamCValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("params")
                    .addParamValue("paramsValue1")
                    .addParamValue("paramsValue2")
                    .setInputBinding(InputBinding.newBuilder().setPosition(2).setPrefix("--prefix").build())
                    .build()
            )
            .addOutputs(
                JobParam.newBuilder()
                    .setParamName("ParamD")
                    .setType("STAC")
                    .setOutputBinding(OutputBinding.newBuilder().setGlob(".").build())
                    .build())
            .addOutputs(
                JobParam.newBuilder()
                    .setParamName("ParamE")
                    .addParamValue("ThisShouldNotBeSerialized")
            )
            .putEnvironmentVariables("envKey", "envValue")
            .build();
        String existingClaimName = null;
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-CWL.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_HasCWLRelatedParametersAndOutputBasePathFromOutputGlob_WhenDescriptorTypeIsCWLAndOutputIsFileType() throws Exception {
        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);

        JobSpec jobSpec = createDefaultJobSpecBuilderWithDockerCommandsAndArguments()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                    .build()
            )
            .addOutputs(
                JobParam.newBuilder()
                    .setParamName("FileOutput")
                    .setType("URL")
                    .setOutputBinding(OutputBinding.newBuilder().setGlob("./out").build())
                    .build())
            .putEnvironmentVariables("envKey", "envValue")
            .build();

        String existingClaimName = null;
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-CWL-and-file-output.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_BuildsProcessingTemplateWithPathsOfTheStageInCatalogsAsDockerArguments_WhenInputParamsContainSTACTypeAndDescriptorTypeIsCWL() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());

        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .setService(
                Service.newBuilder().setDockerImageTag("test:1.1").setId("theId").setDescriptorType("CWL")
                    .build())
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .addParamValue("ParamAValue2")
                    .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamBWithPrefix")
                    .addParamValue("test://this-will-be-overridden-with-a-path-to-stageIn-catalog-with-prefix.json")
                    .setType("STAC")
                    .setInputBinding(InputBinding.newBuilder().setPosition(0).setPrefix("--prefix").build())
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamBWithoutPrefix")
                    .addParamValue("test://this-will-be-overridden-with-a-path-to-stageIn-catalog-without-prefix.json")
                    .setType("Stac")
                    .setInputBinding(InputBinding.newBuilder().setPosition(2).build())
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamC")
                    .addParamValue("ParamCValue1")
                    .build()
            )
            .build();
        String existingClaimName = null;
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-args-STAC-catalog-from-CWLInputBinding.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflowW_BuildsProcessingTemplateWithShareMemory_WhenSharedMemoryIsSet() throws Exception {

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(createDefaultLegacyWorkflowProperties());

        JobSpec.Builder defaultJobSpecBuilder = createDefaultJobSpecBuilder();
        defaultJobSpecBuilder.setResourceRequest(

            ResourceRequest.newBuilder()
                .setSharedMemory(SharedMemory.newBuilder().setSize("1Gi").build())
        );

        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(defaultJobSpecBuilder.build(), null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-shared-memory.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetLegacyWorkflow_ReturnsWorkflowWithConfigMapForOutputUploaderStep_WhenOutputUploaderConfigMapNameIsSet() throws Exception {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setOutputUploaderConfigMapName("outputUploaderConfigMapName");

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);
        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .build()
            ).build();
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-output-uploader-configmap.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, WORKFLOW_PARAMETERS_COMPARATOR);

    }

    @Test
    public void testGetLegacyWorkflow_ReturnsWorkflowWithConfigMapForInputDownloaderStep_WhenInputDownloaderConfigMapNameIsSet() throws Exception {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setInputDownloaderConfigMapName("inputDownloaderConfigMapName");

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);
        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .build()
            ).build();
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-input-downloader-configmap.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, WORKFLOW_PARAMETERS_COMPARATOR);

    }

    @Test
    public void testGetLegacyWorkflow_ReturnsWorkflowWithoutConfigMapForInputDownloaderStep_WhenInputDownloaderConfigMapNameIsEmpty() throws Exception {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setInputDownloaderConfigMapName("");

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);
        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .build()
            ).build();
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, WORKFLOW_PARAMETERS_COMPARATOR);

    }

    @Test
    public void testGetLegacyWorkflow_ReturnsWorkflowWithConfigMapForInputDownloaderAndOutputUploaderSteps_WhenInputDownloaderAndOutputUploaderConfigMapNamesAreSet() throws Exception {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setInputDownloaderConfigMapName("inputDownloaderConfigMapName");
        workflowProperties.setOutputUploaderConfigMapName("outputUploaderConfigMapName");

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);
        JobSpec jobSpec = createDefaultJobSpecBuilder()
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamA")
                    .addParamValue("ParamAValue1")
                    .build()
            )
            .addInputs(
                JobParam.newBuilder()
                    .setParamName("ParamB")
                    .addParamValue("ParamBValue1")
                    .setType("URL")
                    .build()
            ).build();
        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, null);

        String actualWorkflowJson = GSON.toJson(workflow);
        String expectedWorkflowJson = readAsString(BASE_TEST_PATH.resolve("legacy-workflow-with-configmap.json"));
        JSONAssert.assertEquals(expectedWorkflowJson, actualWorkflowJson, WORKFLOW_PARAMETERS_COMPARATOR);

    }

    @Test
    public void testGetLegacyWorkflow_LeavesDockerImageTagAsIs_WhenInternalPlatformRegistryPrefixIsEmpty() {

        LegacyWorkflowProperties workflowProperties = createDefaultLegacyWorkflowProperties();
        workflowProperties.setInternalPlatformRegistryPrefix("");

        CoreLegacyWorkflowBuilder coreLegacyWorkflowBuilder = new CoreLegacyWorkflowBuilder(workflowProperties);
        JobSpec jobSpec = createDefaultJobSpecBuilder().build();

        Workflow workflow = coreLegacyWorkflowBuilder.getLegacyWorkflow(jobSpec, null);

        String actualWorkflowJson = GSON.toJson(workflow);
        DocumentContext workflowResult = JsonPath.parse(actualWorkflowJson);
        assertThat(workflowResult.<String>read("spec.templates[2].container.image"))
                .isEqualTo("test:1.1");
    }

    private JobSpec.Builder createDefaultJobSpecBuilder() {
        return JobSpec.newBuilder()
            .setService(
                Service.newBuilder().setDockerImageTag("test:1.1").setId("theId")
                    .build())
            .putEnvironmentVariables("KEY1", "VALUE1")
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                .setId("theJobId")
                .setIntJobId("30")
                .setUserId("theJobOwner")
                .build());
    }

    private static JobSpec.Builder createDefaultJobSpecWithUserMountClaim() {
        UserMount userMount = UserMount.newBuilder().
            setMountPath("mountPath")
            .setType("ro")
            .setName("bps")
            .setTargetPath("/target")
            .build();
        return JobSpec.newBuilder()
            .setService(
                Service.newBuilder().setDockerImageTag("test:1.1").setId("theId")
                    .build())
            .putEnvironmentVariables("KEY1", "VALUE1")
            .setKind(Kind.WORKFLOW)
            .addAllUserMount(Collections.singletonList(userMount))
            .setJob(Job.newBuilder()
                .setId("theJobId")
                .setIntJobId("30")
                .setUserId("theJobOwner")
                .build());
    }

    private static JobSpec.Builder createDefaultJobSpecWithUUIDBuilder() {
        return JobSpec.newBuilder()
            .setService(
                Service.newBuilder().setDockerImageTag("test:1.1").setId("theId")
                    .build())
            .putEnvironmentVariables("KEY1", "VALUE1")
            .setKind(Kind.WORKFLOW)
            .setJob(Job.newBuilder()
                .setId("theJobId")
                .setIntJobId("30")
                .setUserId("theJobOwner")
                .setUserUUID("UserUUID")
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

    private static LegacyWorkflowProperties addEnvironment(LegacyWorkflowProperties legacyWorkflowProperties) {

        legacyWorkflowProperties.getInputDownloader().getEnvs().add(new LegacyWorkflowProperties.Environment("InputDownloaderNameEnv", new LegacyWorkflowProperties.ValueFrom(
            new LegacyWorkflowProperties.Secret("InputDownloaderSecretName", "InputDownloaderSecretKey")
        )));

        legacyWorkflowProperties.getProcessing().getEnvs().add(new LegacyWorkflowProperties.Environment("ProcessingNameEnv", new LegacyWorkflowProperties.ValueFrom(
            new LegacyWorkflowProperties.Secret("ProcessingSecretName", "ProcessingSecretKey")
        )));

        legacyWorkflowProperties.getOutputUploader().getEnvs().add(new LegacyWorkflowProperties.Environment("OutputUploaderNameEnv", new LegacyWorkflowProperties.ValueFrom(
            new LegacyWorkflowProperties.Secret("OutputUploaderSecretName", "OutputUploaderSecretKey")
        )));

        return legacyWorkflowProperties;
    }

    private JobSpec.Builder createDefaultJobSpecBuilderWithDockerCommandsAndArguments() {
        return createDefaultJobSpecBuilder()
            .setService(Service.newBuilder().setDockerImageTag("test:1.1").setId("theId").setDescriptorType("CWL")
                .build())
            .setDockerCommand("dockerCommand")
            .addAllDockerArguments(ImmutableList.of("arg1", "arg2"));
    }

    private static Customization regexMatcher(String jsonPath) {
        return new Customization(jsonPath, new RegularExpressionValueMatcher<>());
    }

}
