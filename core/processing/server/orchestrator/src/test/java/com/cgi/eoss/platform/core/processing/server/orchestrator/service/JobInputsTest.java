package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class JobInputsTest {

    @Test
    public void testJobInputs_CreatesJobInputsWithJobIdAndJobInputObjectsInJobInputMap() {
        User user = new User("platformUser");
        PlatformService platformService = ProcessingCoreEntities.createPlatformService(user)
                .platformServiceDescriptor(PlatformServiceDescriptor.builder()
                        .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                                                .id("input_1")
                                                .defaultAttrs(ImmutableMap.of("dataType", "string"))
                                                .platformMetadata(ImmutableMap.of("preventUrlDownload", "false", "type", "STAC", "format", "CATALOGUE"))
                                        .build(),
                                PlatformServiceDescriptor.Parameter.builder()
                                        .id("input_2")
                                        .defaultAttrs(ImmutableMap.of("dataType", "string"))
                                        .platformMetadata(ImmutableMap.of("preventUrlDownload", "true", "format", "OTHER"))
                                        .build()))
                        .build())
                .build();
        JobConfig jobConfig = new JobConfig(user, platformService);

        Multimap<String, String> inputs = HashMultimap.create();
        inputs.put("input_1", "value_input_1_1");
        inputs.put("input_1", "value_input_1_2");
        inputs.put("input_2", "value_input_2_1");
        inputs.put("input_2", "value_input_2_2");
        inputs.put("unknown_input", "unknown_value");
        jobConfig.setInputs(inputs);
        JobInputs jobInputs = new JobInputs("jobId", user.getName(), jobConfig);

        assertThat(jobInputs.getJobId()).isEqualTo("jobId");
        assertThat(jobInputs.getUserName()).isEqualTo(user.getName());

        Map<String, JobInput> jobInputMap = jobInputs.getInputs();
        assertThat(jobInputMap).hasSize(3);
        JobInput jobInput1 = jobInputMap.get("input_1");
        assertThat(jobInput1.getId()).isEqualTo("input_1");
        assertThat(jobInput1.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(jobInput1.getValues()).containsExactlyInAnyOrder("value_input_1_1", "value_input_1_2");
        assertThat(jobInput1.isParallelInput()).isFalse();
        JobInput jobInput2 = jobInputMap.get("input_2");
        assertThat(jobInput2.getId()).isEqualTo("input_2");
        assertThat(jobInput2.getType()).isEqualTo(JobInput.Type.OTHER);
        assertThat(jobInput2.getValues()).containsExactlyInAnyOrder("value_input_2_1", "value_input_2_2");
        assertThat(jobInput2.isParallelInput()).isFalse();
        JobInput unknownJobinput = jobInputMap.get("unknown_input");
        assertThat(unknownJobinput.getId()).isEqualTo("unknown_input");
        assertThat(unknownJobinput.getType()).isEqualTo(JobInput.Type.OTHER);
        assertThat(unknownJobinput.getValues()).containsExactly("unknown_value");
        assertThat(unknownJobinput.isParallelInput()).isFalse();
    }

    @Test
    public void testJobInputs_CreatesJobInputsWithEmptyInputs_WhenInputsListIsEmpty() {
        User user = new User("platformUser");
        PlatformService platformService = ProcessingCoreEntities.createPlatformService(user)
                .platformServiceDescriptor(PlatformServiceDescriptor.builder().build())
                .build();
        JobConfig jobConfig = new JobConfig(user, platformService);
        jobConfig.setInputs(HashMultimap.create());
        JobInputs jobInputs = new JobInputs("jobId", user.getName(), jobConfig);
        assertThat(jobInputs.getJobId()).isEqualTo("jobId");
        assertThat(jobInputs.getUserName()).isEqualTo(user.getName());
        assertThat(jobInputs.getInputs()).isEmpty();
    }

    @Test
    public void testGet_ReturnsJobInputWithCorrespondingKey() {
        final String inputId = "input";
        JobInput input = JobInput.builder()
                .id(inputId)
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(inputId, input))
                .build();
        assertThat(jobInputs.get(inputId)).isEqualTo(input);
    }

    @Test
    public void testGet_ReturnsNull_WhenJobInputWithCorrespondingKeyIsNotPresent() {
        final String inputId = "nonExistingId";
        JobInput input = JobInput.builder()
                .id("input")
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of("input", input))
                .build();
        assertThat(jobInputs.get(inputId)).isNull();
    }

    @Test
    public void testGetValuesMap_ReturnsJobInputsAsMultimap() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of("input_1", JobInput.builder().values(ImmutableList.of("value_input_1_1", "value_input_1_2")).build(),
                        "input_2", JobInput.builder().values(ImmutableList.of("value_input_2_1", "value_input_2_2")).build()))
                .build();
        Map<String, Collection<String>> actualValuesMap = jobInputs.getValuesMap().asMap();
        assertThat(actualValuesMap).hasSize(2);
        assertThat(actualValuesMap.get("input_1")).containsExactlyInAnyOrder("value_input_1_1", "value_input_1_2");
        assertThat(actualValuesMap.get("input_2")).containsExactlyInAnyOrder("value_input_2_1", "value_input_2_2");
    }

    @Test
    public void testCloneWithParallelizedInput_ReturnsInstanceCloneWithTargetInputFlaggedAsParallelAndWithReplacedValue_WhenParallelInputsKeyIsNotLegacy() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "input_1",
                        JobInput.builder().id("input_1").values(ImmutableList.of("value_input_1", "value_input_2")).build(),
                        "input_2",
                        JobInput.builder().id("input_2").values(ImmutableList.of("value_input_2")).build()))
                .build();

        JobInputs actualClonedJobInputs = jobInputs.cloneWithParallelizedInput("input_1",  "value_input_1");
        assertThat(actualClonedJobInputs).isEqualTo(
                JobInputs.builder()
                        .inputs(ImmutableMap.of(
                                "input_1",
                                JobInput.builder().id("input_1").parallelInput(true).values(ImmutableList.of("value_input_1")).build(),
                                "input_2",
                                JobInput.builder().id("input_2").values(ImmutableList.of("value_input_2")).build()))
                        .build()
        );
    }

    @Test
    public void testCloneWithParallelizedInput_ReturnsInstanceCloneWithTargetInputFlaggedAsParallelAndWithReplacedKeyAndValue_WhenParallelInputsKeyIsLegacy() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "parallelInputs",
                        JobInput.builder().id("input_1").values(ImmutableList.of("value_input_1", "value_input_2")).build(),
                        "input_2",
                        JobInput.builder().id("input_2").values(ImmutableList.of("value_input_2")).build()))
                .build();

        JobInputs actualClonedJobInputs = jobInputs.cloneWithParallelizedInput("parallelInputs",  "value_input_1");
        assertThat(actualClonedJobInputs).isEqualTo(
                JobInputs.builder()
                        .inputs(ImmutableMap.of(
                                "input",
                                JobInput.builder().id("input").parallelInput(true).values(ImmutableList.of("value_input_1")).build(),
                                "input_2",
                                JobInput.builder().id("input_2").values(ImmutableList.of("value_input_2")).build()))
                        .build()
        );
    }

    @Test
    public void testContainStacInput_ReturnsTrue_WhenJobInputsContainJobInputOfSTACType() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "stacInput",
                        JobInput.builder()
                                .id("input_1")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("value_input_1", "value_input_2"))
                                .build(),
                        "nonStacInput",
                        JobInput.builder()
                                .id("input_2")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("value_input_2"))
                                .build()))
                .build();
        assertThat(jobInputs.containStacInput()).isTrue();
    }

    @Test
    public void testContainStacInput_ReturnsFalse_WhenJobInputsDoNotContainJobInputOfSTACType() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "nonStacInput1",
                        JobInput.builder()
                                .id("input_1")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("value_input_1", "value_input_2"))
                                .build(),
                        "nonStacInput2",
                        JobInput.builder()
                                .id("input_2")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("value_input_2"))
                                .build()))
                .build();
        assertThat(jobInputs.containStacInput()).isFalse();
    }

    @Test
    public void testWithoutFragments_ReturnsJobInputsWithFragmentsRemovedFromJobInputOfSTACTypeOnly_WhenJobInputsContainJobInputOfSTACTypeWithFragment() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "stacInputWithFragment",
                        JobInput.builder()
                                .id("input_1")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("valueOne#fragment", "valueTwo#fragment"))
                                .build(),
                        "stacInputWithoutFragment",
                        JobInput.builder()
                                .id("input_2")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("value"))
                                .build(),
                    "URLInputWithFragment",
                        JobInput.builder()
                            .id("input_3")
                            .type(JobInput.Type.URL)
                            .values(ImmutableList.of("valueURL#fragment"))
                            .build())
                )
                .build();
        assertThat(jobInputs.withoutFragments()).isEqualTo(JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "stacInputWithFragment",
                        JobInput.builder()
                                .id("input_1")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("valueOne", "valueTwo"))
                                .build(),
                        "stacInputWithoutFragment",
                        JobInput.builder()
                                .id("input_2")
                                .type(JobInput.Type.STAC)
                                .values(ImmutableList.of("value"))
                                .build(),
                        "URLInputWithFragment",
                        JobInput.builder()
                                .id("input_3")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("valueURL#fragment"))
                                .build())
                )
                .build());
    }

    @Test
    public void testWithoutFragments_ReturnsJobInputsAsTheyAre_WhenJobInputsDoNotContainJobInputOfSTACType() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(
                        "input_1",
                        JobInput.builder()
                                .id("input_1")
                                .type(JobInput.Type.URL)
                                .values(ImmutableList.of("someValue"))
                                .build())
                )
                .build();
        assertThat(jobInputs.withoutFragments()).isEqualTo(jobInputs);
    }

    @Test
    public void testCloneWithNewInput_AddsInputWithAllFieldsPresent() {
        User user = new User("platformUser");
        JobConfig jobConfig = new JobConfig(user, ProcessingCoreEntities.createPlatformService(user)
                .platformServiceDescriptor(PlatformServiceDescriptor.builder()
                        .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                                .id("parallelInputs")
                                .defaultAttrs(ImmutableMap.of("dataType", "string"))
                                .platformMetadata(ImmutableMap.of("type", "STAC", "format", "CATALOGUE"))
                                .build()))
                        .build())
                .build());
        jobConfig.setInputs(HashMultimap.create());

        JobInput added = new JobInputs("jobId", user.getName(), jobConfig)
                .cloneWithNewInput("parallelInputs", ImmutableList.of("sentinel2:///product.SAFE"), jobConfig)
                .getInputs().get("parallelInputs");

        assertThat(added.getId()).isEqualTo("parallelInputs");
        assertThat(added.getType()).isEqualTo(JobInput.Type.STAC);
        assertThat(added.getValues()).containsExactly("sentinel2:///product.SAFE");
        assertThat(added.isParallelInput()).isFalse();
    }

    @Test
    public void testCloneWithNewInput_DoesNotModifyOriginalInstance() {
        User user = new User("platformUser");
        JobConfig jobConfig = new JobConfig(user, ProcessingCoreEntities.createPlatformService(user)
                .platformServiceDescriptor(PlatformServiceDescriptor.builder().build())
                .build());
        Multimap<String, String> inputs = HashMultimap.create();
        inputs.put("collection", "out");
        inputs.put("geoServerSpec", "{}");
        jobConfig.setInputs(inputs);
        JobInputs original = new JobInputs("jobId", user.getName(), jobConfig);

        original.cloneWithNewInput("parallelInputs", ImmutableList.of("sentinel2:///product.SAFE"), jobConfig);

        assertThat(original.getInputs()).hasSize(2).containsOnlyKeys("collection", "geoServerSpec");
        assertThat(original.get("collection").getValues()).containsExactly("out");
        assertThat(original.get("geoServerSpec").getValues()).containsExactly("{}");
    }

    @Test
    public void testCloneWithNewInput_ThrowsException_WhenInputIdAlreadyExists() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of("parallelInputs",
                        JobInput.builder().id("parallelInputs").values(ImmutableList.of("existing")).build()))
                .build();

        assertThatThrownBy(() -> jobInputs.cloneWithNewInput("parallelInputs", ImmutableList.of("new"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}