package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JobParamsCreatorTest {

    private static final String FORMAT = "format";

    private static final String CATALOGUE = "CATALOGUE";

    private static final String OTHER = "OTHER";

    private static final String TYPE = "type";

    private static final String STAC = "STAC";

    private static final String PREVENT_URL_DOWNLOAD = "preventUrlDownload";

    private static final String DATA_TYPE = "dataType";

    private static final String STRING = "string";

    private static final String URL = "URL";

    @Test
    public void testCreateJobParams_ThrowsNullPointerException_WhenServiceDescriptorParametersIsEmptyAndJobInputsAreNull() {

        assertThatThrownBy(() -> JobParamsCreator.createJobParams(ImmutableList.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testCreateJobParams_ReturnsAnEmptyList_WhenServiceParametersAndJobInputsAreEmpty() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(ImmutableList.of(), JobInputs.builder().inputs(Collections.emptyMap()).build());
        assertThat(jobParams).isEmpty();

    }

    @Test
    public void testCreateJobParams_ReturnsAnEmptyList_WhenServiceDescriptorParametersDoNotMatchWithJobInputs() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                        ImmutableMap.of("paramB",
                                JobInput.builder().type(JobInput.Type.OTHER).id("paramB").values(ImmutableList.of("paramValueB")).build()))
                        .build()
        );
        assertThat(jobParams).isEmpty();
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorAndJobInputsToJobParams() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false"))
                        .build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()))
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersMatchingWithJobInputsToJobParams_WhenServiceDescriptorAndJobInputsPartiallyMatch() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build(),
                                        "paramB",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramB").values(ImmutableList.of("paramValueB")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsAllServiceDescriptorAndJobInputsToJobParams_WhenAllServiceDescriptorAndJobInputsMatch() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("paramA")
                                .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("paramB")
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build(),
                                        "paramB",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramB").values(ImmutableList.of("paramValueB")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build(),
                JobParam.newBuilder().setType(URL).setParamName("paramB").addParamValue("paramValueB").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsCollectionJobInputToJobParams_WhenServiceDescriptorParametersIsNull() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                null,
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("collection",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("collection").values(ImmutableList.of("collectionValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("collection").addParamValue("collectionValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsCollectionJobInputToJobParams_WhenServiceDescriptorParametersIsEmpty() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("collection",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("collection").values(ImmutableList.of("collectionValueA")).build()
                                        )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("collection").addParamValue("collectionValueA").build()
        );
    }


    @Test
    public void testCreateJobParams_MapsCollectionJobInputToJobParams_WhenCollectionServiceDescriptorParameterIsNotPresent() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("paramA")
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build(),
                                        "collection",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("collection").values(ImmutableList.of("collectionValue")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build(),
                JobParam.newBuilder().setType(OTHER).setParamName("collection").addParamValue("collectionValue").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobInputToDownloadableJobParam_WhenInputServiceDescriptorParameterIsNotPresent() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("paramA")
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build(),
                                        "input",
                                        JobInput.builder().type(JobInput.Type.URL).id("input").values(ImmutableList.of("InputValue")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build(),
                JobParam.newBuilder().setType(URL).setParamName("input").addParamValue("InputValue").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobInputToNotDownloadableJobParam_WhenInputServiceDescriptorParameterIsPresentAndPreventUrlDownloadIsTrue() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "true"))
                        .id("input").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("input",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("input").values(ImmutableList.of("inputValue")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("input").addParamValue("inputValue").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobInputToDownloadableJobParam_WhenInputServiceDescriptorParameterIsPresent() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("paramA")
                                .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("input")
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build(),
                                        "input",
                                        JobInput.builder().type(JobInput.Type.URL).id("input").values(ImmutableList.of("InputValue")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build(),
                JobParam.newBuilder().setType(URL).setParamName("input").addParamValue("InputValue").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersMatchingWithJobInputToJobParamWithOTHERType_WhenPlatformMetadataPreventUrlDownloadIsTrue() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "true"))
                        .id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("paramA").addParamValue("paramValueA").build()
        );

    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersMatchingWithJobInputToJobParamWithOTHERType_WhenPlatformMetadataPreventUrlDownloadIsTrueNonCaseSensitive() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "TrUe"))
                        .id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersMatchingWithJobInputToJobParamWithURLType_WhenPlatformMetadataPreventUrlDownloadIsNotTrue(){
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobInputWithInputBinding_WhenDescriptorContainsNonNullInputBindingField() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).build())
                        .build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramAValueA").setInputBinding(InputBinding.newBuilder().setPosition(1).build()).build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobInputWithInputBindingWithPrefix_WhenDescriptorContainsNonNullInputBindingFieldWithPrefix() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).prefix("--prefix").build())
                        .build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramAValueA").setInputBinding(
                        InputBinding.newBuilder().setPosition(1).setPrefix("--prefix").build()).build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobInputWithInputBindingOnlyForParameterContainingBindingsInTheDescriptor() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).build()).build(),
                        PlatformServiceDescriptor.Parameter.builder()
                        .id("paramB").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build(),
                                        "paramB",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramB").values(ImmutableList.of("paramBValueB")).build()

                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramAValueA").setInputBinding(InputBinding.newBuilder().setPosition(1).build()).build(),
                JobParam.newBuilder().setType(URL).setParamName("paramB").addParamValue("paramBValueB").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersToJobParamWithSTACType_WhenPlatformMetadataTypeIsSTAC() throws MalformedURLException {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false", TYPE, STAC))
                        .id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.STAC).id("paramA")
                                                .values(ImmutableList.of("paramValueA"))
                                                .internalReference(new URL("http://reference"))
                                                .build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(STAC).setParamName("paramA").addParamValue("http://reference").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersToJobParamWithSTACType_WhenPlatformMetadataTypeIsSTACAndPreventUrlDownloadIsTrue() throws MalformedURLException {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "true", TYPE, STAC))
                        .id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.STAC).id("paramA")
                                                .internalReference(new URL("http://reference"))
                                                .values(ImmutableList.of("paramValueA"))
                                                .build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(STAC).setParamName("paramA").addParamValue("http://reference").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsServiceDescriptorParametersToJobParamWithSTACType_WhenPlatformMetadataTypeIsSTACRegardlessOfCase() throws MalformedURLException {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false", TYPE, "STac"))
                        .id("paramA").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.STAC).id("paramA").internalReference(new URL("http://reference"))
                                                .values(ImmutableList.of("paramValueA"))
                                                .build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(STAC).setParamName("paramA").addParamValue("http://reference").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsJobInputToJobParamValueOnce_WhenJobInputsContainSplitInputValue() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .title("input_id")
                        .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false"))
                        .id("input_id").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("input_id",
                                        JobInput.builder()
                                                .type(JobInput.Type.URL)
                                                .id("input_id")
                                                .values(ImmutableList.of("http://first.url", "http://second.url"))
                                                .build()
                                )
                        )
                        .build()
        );
        assertThat(jobParams).hasSize(1);
        JobParam jobParam = jobParams.get(0);
        assertThat(jobParam.getType()).isEqualTo(URL);
        assertThat(jobParam.getParamName()).isEqualTo("input_id");
        assertThat(jobParam.getParamValueList()).containsExactlyInAnyOrder(
                "http://first.url",
                "http://second.url"
        );
    }

    @Test
    public void testCreateJobParams_MapsStacJobInputInternalReferenceToJobParamValue_WhenPlatformMetadataContainsTypeFieldWithStacValueAndJobInputsContainSTACInputType() throws Exception {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .title("stacSearch")
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false", TYPE, STAC))
                        .id("stacSearch").build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("stacSearch",
                                        JobInput.builder()
                                                .type(JobInput.Type.STAC)
                                                .id("stacSearch")
                                                .values(ImmutableList.of("http://stac.url/path/to/catalog.json#featureIdA",
                                                        "http://stac.url/path/to/catalog.json#featureIdB"))
                                                .internalReference(new URL("http://internal.url/path/to/catalog.json"))
                                                .build()
                                )
                        )
                        .build()
        );
        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(STAC).setParamName("stacSearch")
                        .addParamValue("http://internal.url/path/to/catalog.json")
                        .build()
        );
    }

    @Test
    public void testCreateJobParams_MapsParallelStacJobInputInternalReferenceToJobParamValue_WhenJobInputsContainSTACParallelInputTypeWithInputKey() throws Exception {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("input",
                                        JobInput.builder()
                                                .type(JobInput.Type.STAC)
                                                .id("input")
                                                .values(ImmutableList.of("http://stac.url/path/to/catalog.json#featureIdA"))
                                                .internalReference(new URL("http://internal.url/path/to/catalog.json"))
                                                .parallelInput(true)
                                                .build(),
                                        "paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );
        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(STAC).setParamName("input")
                        .addParamValue("http://internal.url/path/to/catalog.json#featureIdA")
                        .build(),
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateOutputJobParams_MapsOutputServiceDescriptorParameterToJobParamOutputBindingWithURLType_WhenDescriptorContainsOutputBindingFieldAndPlatformMetadataDescribingURLType() {
        List<PlatformServiceDescriptor.Parameter> parameters = ImmutableList.of(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("with-binding")
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false"))
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("globValue").build())
                        .build()
        );
        List<JobParam> jobParams = JobParamsCreator.createOutputJobParams(parameters);

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder()
                        .setParamName("with-binding")
                        .setType(URL)
                        .setOutputBinding(OutputBinding.newBuilder().setGlob("globValue").build())
                        .build()
        );
    }

    @Test
    public void testCreateOutputJobParams_MapsOutputServiceDescriptorParameterToJobParamWithSTACType_WhenDescriptorContainsPlatformMetadataDescribingSTACType() {
        List<PlatformServiceDescriptor.Parameter> parameters = ImmutableList.of(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("with-binding")
                        .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false", TYPE, STAC))
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("globValue").build())
                        .build()
        );
        List<JobParam> jobParams = JobParamsCreator.createOutputJobParams(parameters);

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder()
                        .setParamName("with-binding")
                        .setType(STAC)
                        .setOutputBinding(OutputBinding.newBuilder().setGlob("globValue").build())
                        .build()
        );
    }

    @Test
    public void testCreateOutputJobParams_DoesNotMapOutputServiceDescriptorParameterToJobParamOutputBinding_WhenDescriptorDoesNotContainOutputBindingField() {
        List<PlatformServiceDescriptor.Parameter> parameters = ImmutableList.of(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("without-binding")
                        .build()
        );
        List<JobParam> jobParams = JobParamsCreator.createOutputJobParams(parameters);

        assertThat(jobParams).isEmpty();
    }

    @Test
    public void testCreateJobParams_MapsParallelStacJobInputInternalReferenceToJobParamValue_WhenJobInputsContainSTACParallelInputTypeWithCustomInputKey() throws Exception {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(TYPE, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                        .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                                .id("customParallelInputKey")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(TYPE, STAC, PREVENT_URL_DOWNLOAD, "false"))
                                .build()),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("customParallelInputKey",
                                        JobInput.builder()
                                                .type(JobInput.Type.STAC)
                                                .id("customParallelInputKey")
                                                .values(ImmutableList.of("http://stac.url/path/to/catalog.json#featureIdA"))
                                                .internalReference(new URL("http://internal.url/path/to/catalog.json"))
                                                .parallelInput(true)
                                                .build(),
                                        "paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );
        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(STAC).setParamName("customParallelInputKey")
                        .addParamValue("http://internal.url/path/to/catalog.json#featureIdA")
                        .build(),
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterWithSubsetting_WhenServiceDescriptorContainsSubsetting() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                            .id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                            .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format(FORMAT).build())
                            .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                            .id("aoi")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, "AOI"))
                            .build()),
                JobInputs.builder().jobId("jobId")
                        .inputs(ImmutableMap.of(
                                "paramA",
                                JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build(),
                                "aoi",
                                JobInput.builder().type(JobInput.Type.URL).id("aoi").values(ImmutableList.of("aoiValue")).build()
                        ))
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("aoi").addParamValue("aoiValue").build(),
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramAValueA").setSubsetting(Subsetting.newBuilder().setAoi("aoiValue").setFormat(FORMAT).build()).build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterWithoutSubsetting_WhenServiceDescriptorContainsEmptySubsetting() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                            .id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                            .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("").format("").build())
                            .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                            .id("aoi")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, "AOI"))
                            .build()),
                JobInputs.builder().jobId("jobId")
                        .inputs(ImmutableMap.of(
                                "paramA",
                                JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build(),
                                "aoi",
                                JobInput.builder().type(JobInput.Type.OTHER).id("aoi").values(ImmutableList.of("aoiValue")).build()
                        ))
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("aoi").addParamValue("aoiValue").build(),
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramAValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterWithoutSubsetting_WhenJobInputsDoNotContainAoiInput() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                            .id("paramA")
                                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                            .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format(FORMAT).build())
                            .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                            .id("aoi")
                                .data(PlatformServiceDescriptor.Parameter.DataNodeType.BOUNDING_BOX)
                                .platformMetadata(ImmutableMap.of(FORMAT, "AOI"))
                            .build()),
                JobInputs.builder().jobId("jobId")
                        .inputs(ImmutableMap.of(
                                "paramA",
                                JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build()
                        ))
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramAValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterWithSubsetting_WhenServiceDescriptorContainsParallelInputsAndJobParamsContainInputOfChildJob() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                            .id("parallelInputs")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                            .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format(FORMAT).build())
                            .build(),
                        PlatformServiceDescriptor.Parameter.builder()
                            .id("aoi")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .data(PlatformServiceDescriptor.Parameter.DataNodeType.BOUNDING_BOX)
                                .platformMetadata(ImmutableMap.of(FORMAT, "AOI"))
                            .build()),
                JobInputs.builder().jobId("jobId")
                        .inputs(ImmutableMap.of(
                                "input",
                                JobInput.builder().type(JobInput.Type.URL).id("input").values(ImmutableList.of("paramAValueA")).build(),
                                "aoi",
                                JobInput.builder().type(JobInput.Type.OTHER).id("aoi").values(ImmutableList.of("aoiValue")).build()
                        ))
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("aoi").addParamValue("aoiValue").build(),
                JobParam.newBuilder().setType(URL).setParamName("input").addParamValue("paramAValueA").setSubsetting(Subsetting.newBuilder().setAoi("aoiValue").setFormat(FORMAT).build()).build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeURL_WhenPlatformMetadataIsEmptyAndDataTypeIsString() {

        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of())
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeURL_WhenPreventUrlDownloadIsFalse() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false"))
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }


    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeOTHER_WhenFormatIsCatalogueAndPreventUrlDownloadIsTrue() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                        .id("paramA")
                        .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                        .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "true"))
                        .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("").format("").build())
                        .build() ),
                JobInputs.builder().jobId("jobId")
                        .inputs(ImmutableMap.of(
                                "paramA",
                                JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramAValueA")).build()

                        ))
                        .build()
        );

        assertThat(jobParams).containsExactly(
                JobParam.newBuilder().setType(OTHER).setParamName("paramA").addParamValue("paramAValueA").build()
        );
    }


    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeURL_WhenFormatIsCatalogueAndPreventUrlDownloadIsMissing() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE))
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeURL_WhenDataTypeIsStringFormatIsOtherAndPreventUrlDownloadIsMissing() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.URL).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(URL).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeOTHER_WhenDataTypeIsStringFormatIsAOIAndPreventUrlDownloadIsMissing() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, "AOI"))
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(OTHER).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeOTHER_WhenDataTypeIsStringAndContainsAllowedValuesAndPreventUrlDownloadIsMissing() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING, "allowedValues", "value1,value2"))
                                .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(OTHER).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }

    @Test
    public void testCreateJobParams_MapsInputJobParameterAsTypeOTHER_WhenDataTypeIsStringFormatIsOtherAndPreventUrlDownloadIsTrue() {
        List<JobParam> jobParams = JobParamsCreator.createJobParams(
                ImmutableList.of(
                        PlatformServiceDescriptor.Parameter.builder().id("paramA")
                                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                                .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                                .build()
                ),
                JobInputs.builder().jobId("jobId").inputs(
                                ImmutableMap.of("paramA",
                                        JobInput.builder().type(JobInput.Type.OTHER).id("paramA").values(ImmutableList.of("paramValueA")).build()
                                )
                        )
                        .build()
        );

        assertThat(jobParams).containsExactlyInAnyOrder(
                JobParam.newBuilder().setType(OTHER).setParamName("paramA").addParamValue("paramValueA").build()
        );
    }


}
