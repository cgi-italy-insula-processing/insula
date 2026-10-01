package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.utils.JobConfigBuilder;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobValidationResult;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class JobInputsValidatorTest {

    private JobInputsValidator jobInputsValidator;
    private static final User PLATFORM_USER = new User("platformUser");

    @Before
    public void init() {
        jobInputsValidator = new JobInputsValidator(Arrays.asList("collection", "geoServerSpec", "odcDatasetSpec"));
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenAllInputsAreValid() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", "valueString");

        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", "valueString");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenAllInputsAreValidAndOptionalInputIsProvided() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("optional_input", "optional");
        multimapInputs.put("int_input", "3");
        multimapInputs.put("double_input", "3.5");
        multimapInputs.put("boolean_input", "true");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("optional_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(0).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("int_input").defaultAttrs(ImmutableMap.of("dataType", "integer"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("double_input").defaultAttrs(ImmutableMap.of("dataType", "double"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("boolean_input").defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("optional_input", "optional");
        expectedInputs.put("int_input", "3");
        expectedInputs.put("double_input", "3.5");
        expectedInputs.put("boolean_input", "true");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceHasNoInputsAndJobOnlyInputIsCollection() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("collection", "{\"output_id_1\":\"eopaas://output\"}");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("collection", "{\"output_id_1\":\"eopaas://output\"}");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceHasNoInputsAndJobOnlyInputIsGeoServerSpec() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("geoServerSpec", "{\"output_id_1\":\"eopaas://output\"}");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("geoServerSpec", "{\"output_id_1\":\"eopaas://output\"}");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceHasNoInputsAndJobOnlyInputIsOdcDatasetSpec() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("odcDatasetSpec", "{\"output_id_1\":\"eopaas://output\"}");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("odcDatasetSpec", "{\"output_id_1\":\"eopaas://output\"}");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenNotAllJobInputsIdAreDeclaredInPlatformServiceInputs() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", "valueString");
        JobConfig config = createJobConfig(multimapInputs, new ArrayList<>(), ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Missing required input(s): string_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", "valueString");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputIsNotOptionalAndValueIsNull() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", null);
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): string_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", null);
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputsAreNotOptionalAndValueIsEmpty() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", "");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): string_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", "");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputMaximumCardinalityIsOneAndInputValueHasSizeGreaterThanOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("string_input", Arrays.asList("valueString1","valueString2"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): string_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("string_input", Arrays.asList("valueString1","valueString2"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputTypeIsStacAndInputValueHasSizeEqualToOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("string_input", Arrays.asList("valueString1"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("type", "STAC", "format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("string_input", Arrays.asList("valueString1"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputTypeStringIsEnumAndValueIsContainedInAllowedValues() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("enum", "a");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("enum").defaultAttrs(ImmutableMap.of("dataType", "string", "allowedValues","a,b,c"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("enum", "a");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputTypeStringIsDateAndValueIsDate() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("date", "2021-03-06T07:21:00");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("date").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "DATE"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("date", "2021-03-06T07:21:00");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputTypeStringIsAoiAndValueIsAoi() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("date", "\"bbox\":[51.9, 7, 52, 7.1],\n" +
                "\"crs\":\"http://www.opengis.net/def/crs/OGC/1.3/CRS84\"");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("date").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("date", "\"bbox\":[51.9, 7, 52, 7.1],\n" +
                "\"crs\":\"http://www.opengis.net/def/crs/OGC/1.3/CRS84\"");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputTypeIntegerMatches() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("int_input", "0");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("int_input").defaultAttrs(ImmutableMap.of("dataType", "integer"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("int_input", "0");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputTypeDoubleMatches() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("double_input", "0.1");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("double_input").defaultAttrs(ImmutableMap.of("dataType", "double"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("double_input", "0.1");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputTypeBooleanMatches() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("boolean_input", "true");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("boolean_input").defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("boolean_input", "true");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputFormatIsCatalogueAndValueIsValidUri() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("directory", "https://basic.test.uri.com/basic_uri_path");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("directory").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE", "preventUrlDownload", "false"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("directory", "https://basic.test.uri.com/basic_uri_path");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputFormatIsCatalogueAndInputTypeIsStacAndValueIsValidUri() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("directory", "https://basic.test.uri.com/basic_uri_path");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("directory").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE", "type", "STAC", "preventUrlDownload", "false"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("directory", "https://basic.test.uri.com/basic_uri_path");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputFormatIsOtherAndInputIsDownloadableStringAndValueIsValidUri() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("file", "https://basic.test.uri.com/basic_uri_path");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("file").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "false"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("file", "https://basic.test.uri.com/basic_uri_path");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputFormatIsCatalogueAndValueIsNotValidUri() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("directory", "$notAUri&%");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("directory").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE", "preventUrlDownload", "false"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): directory");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("directory", "$notAUri&%");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputFormatIsCatalogueAndInputTypeIsStacAndValueIsNotValidUri() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("directory", "$notAUri&%");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("directory").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE", "type", "STAC", "preventUrlDownload", "false"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): directory");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("directory", "$notAUri&%");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputFormatIsOtherAndInputIsDownloadableStringAndValueIsNotValidUri() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("file", "$notAUri&%");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("file").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "false"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): file");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("file", "$notAUri&%");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputTypeStringIsEnumAndValueIsNotContainedInAllowedValues() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("enum", "notInAllowedValues");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("enum").defaultAttrs(ImmutableMap.of("dataType", "string", "allowedValues","a,b,c"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): enum");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("enum", "notInAllowedValues");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputTypeStringIsDateAndValueIsNotDate() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("date", "notADate");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("date").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "DATE"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): date");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("date", "notADate");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputTypeStringIsAoiAndValueIsNotAoi() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("aoi", "notAnAoi");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .platformMetadata(Collections.emptyMap())
                .id("aoi").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): aoi");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("aoi", "notAnAoi");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputTypeIntegerDoesNotMatch() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", "valueString");
        multimapInputs.put("int_input", "notAnInteger");
        multimapInputs.put("double_input", "3.5");
        multimapInputs.put("boolean_input", "true");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("int_input").defaultAttrs(ImmutableMap.of("dataType", "integer"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("double_input").defaultAttrs(ImmutableMap.of("dataType", "double"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("boolean_input").defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): int_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", "valueString");
        expectedInputs.put("int_input", "notAnInteger");
        expectedInputs.put("double_input", "3.5");
        expectedInputs.put("boolean_input", "true");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputTypeDoubleDoesNotMatch() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", "valueString");
        multimapInputs.put("int_input", "3");
        multimapInputs.put("double_input", "notADouble");
        multimapInputs.put("boolean_input", "true");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("int_input").defaultAttrs(ImmutableMap.of("dataType", "integer"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("double_input").defaultAttrs(ImmutableMap.of("dataType", "double"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("boolean_input").defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): double_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", "valueString");
        expectedInputs.put("int_input", "3");
        expectedInputs.put("double_input", "notADouble");
        expectedInputs.put("boolean_input", "true");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputTypeBooleanDoesNotMatch() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("string_input", "valueString");
        multimapInputs.put("int_input", "3");
        multimapInputs.put("double_input", "3.5");
        multimapInputs.put("boolean_input", "notABoolean");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("int_input").defaultAttrs(ImmutableMap.of("dataType", "integer"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("double_input").defaultAttrs(ImmutableMap.of("dataType", "double"))
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("boolean_input").defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): boolean_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("string_input", "valueString");
        expectedInputs.put("int_input", "3");
        expectedInputs.put("double_input", "3.5");
        expectedInputs.put("boolean_input", "notABoolean");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenLiteralDataInputDefaultAttrsDoesNotHaveDataTypeAttribute() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("input", "valueInput");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .defaultAttrs(Collections.emptyMap())
                .id("input")
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isFalse();
        assertThat(validateResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("input", "valueInput");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceAndJobHaveNoInputs() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputDataIsNotLiteral() {

        Set<String> outputId = ImmutableSet.of("output_id_1");
        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.put("input", "valueInput");
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.BOUNDING_BOX)
                .defaultAttrs(Collections.emptyMap())
                .id("input")
                .minOccurs(1).maxOccurs(1).build());
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.COMPLEX)
                .defaultAttrs(Collections.emptyMap())
                .id("input")
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, outputId);
        Job job = createJobFromJobConfig(config);

        JobValidationResult validateResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.put("input", "valueInput");
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputIdIsCollectionAndInputValueHasSizeEqualToOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("collection", Arrays.asList("{\"output_id_1\": \"valueString1\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("collection").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("collection", Arrays.asList("{\"output_id_1\": \"valueString1\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputIdIsGeoserverSpecAndInputValueHasSizeEqualToOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("geoServerSpec", Arrays.asList("{\"output_id_1\": \"valueString1\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("geoServerSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("geoServerSpec", Arrays.asList("{\"output_id_1\": \"valueString1\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputIdIsOdcDatasetSpecAndInputValueHasSizenEqualToOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("odcDatasetSpec", Arrays.asList("{\"output_id_1\": \"valueString1\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("odcDatasetSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("odcDatasetSpec", Arrays.asList("{\"output_id_1\": \"valueString1\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputTypeIsStacAndInputValueHasSizeGreaterThanOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("string_input", Arrays.asList("valueString1","valueString2"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("type", "STAC", "format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): string_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("string_input", Arrays.asList("valueString1","valueString2"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputIdIsCollectionAndInputValueHasSizeGreaterThanOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("collection", Arrays.asList("{\"keyString1\": \"valueString1\"}","{\"keyString2\": \"valueString2\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("collection").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): collection");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("collection", Arrays.asList("{\"keyString1\": \"valueString1\"}","{\"keyString2\": \"valueString2\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);
    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputIdIsGeoserverSpecAndInputValueHasSizeGreaterThanOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("geoServerSpec", Arrays.asList("{\"keyString1\": \"valueString1\"}","{\"keyString2\": \"valueString2\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("geoServerSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): geoServerSpec");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("geoServerSpec", Arrays.asList("{\"keyString1\": \"valueString1\"}","{\"keyString2\": \"valueString2\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputIdIsOdcDatasetSpecAndInputValueHasSizeGreaterThanOne() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("odcDatasetSpec", Arrays.asList("{\"keyString1\": \"valueString1\"}","{\"keyString2\": \"valueString2\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("odcDatasetSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): odcDatasetSpec");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("odcDatasetSpec", Arrays.asList("{\"keyString1\": \"valueString1\"}","{\"keyString2\": \"valueString2\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);
    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenInputIsMandatoryAndInputSizeIsLowerThanMinOccurs() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("string_input", ImmutableList.of("string_input_1"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("string_input").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(2).maxOccurs(2).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);

        User user = new User("platformUser");
        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), user.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Invalid number of inputs for input(s): string_input");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("string_input", ImmutableList.of("string_input_1"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputIdIsCollectionAndInputValueIsAJson() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("collection", Collections.singletonList("{\"output_id_1\":\"defaultOutput_100\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("collection").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(0).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = createJobFromJobConfig(config);

        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("collection", Collections.singletonList("{\"output_id_1\":\"defaultOutput_100\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputIdIsGeoserverSpecAndInputValueIsAJson() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("geoServerSpec", Collections.singletonList("{\"output_id_1\":\"defaultOutput_100\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("geoServerSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(0).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = createJobFromJobConfig(config);

        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("geoServerSpec", Collections.singletonList("{\"output_id_1\":\"defaultOutput_100\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceInputIdIsOdcDatasetSpecAndInputValueIsAJson() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("odcDatasetSpec", Collections.singletonList("{\"output_id_1\":\"defaultOutput_100\"}"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("odcDatasetSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(0).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = createJobFromJobConfig(config);

        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("odcDatasetSpec", Collections.singletonList("{\"output_id_1\":\"defaultOutput_100\"}"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputIdIsCollectionAndInputValueIsNotAJson() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("collection", Collections.singletonList("notAJson"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("collection").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(0).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = createJobFromJobConfig(config);

        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): collection");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("collection", Collections.singletonList("notAJson"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputIdIsGeoserverSpecAndInputValueIsNotAJson() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("geoServerSpec", Arrays.asList("notAJson"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("geoServerSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = createJobFromJobConfig(config);

        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): geoServerSpec");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("geoServerSpec", Arrays.asList("notAJson"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    @Test
    public void testValidate_ReturnsNotValidValidationResult_WhenServiceInputIdIsOdcDatasetSpecAndInputValueIsNotAJson() {

        Multimap<String, String> multimapInputs = ArrayListMultimap.create();
        multimapInputs.putAll("odcDatasetSpec", Arrays.asList("notAJson"));
        List<PlatformServiceDescriptor.Parameter> dataInputs = new ArrayList<>();
        dataInputs.add(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("odcDatasetSpec").defaultAttrs(ImmutableMap.of("dataType", "string"))
                .minOccurs(1).maxOccurs(1).build());
        JobConfig config = createJobConfig(multimapInputs, dataInputs, ImmutableSet.of("output_id_1"));
        Job job = createJobFromJobConfig(config);

        JobValidationResult actualValidationResult = jobInputsValidator.validate(job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): odcDatasetSpec");
        Multimap<String, String> expectedInputs = ArrayListMultimap.create();
        expectedInputs.putAll("odcDatasetSpec", Arrays.asList("notAJson"));
        assertThat(job.getConfig().getInputs()).isEqualTo(expectedInputs);

    }

    private static Job createJobFromJobConfig(JobConfig config) {

        Job job = new Job();
        job.setOwner(config.getOwner());
        job.setConfig(config);
        return job;

    }

    private static JobConfig createJobConfig(Multimap<String, String> multimapInputs, List<PlatformServiceDescriptor.Parameter> dataInputs, Set<String> outputIds) {
        List<PlatformServiceDescriptor.Parameter> dataOutputs = new ArrayList<>();
        for (String outputId : outputIds) {
            dataOutputs.add(PlatformServiceDescriptor.Parameter.builder().id(outputId).build());
        }

        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataOutputs(dataOutputs);
        platformServiceDescriptor.setDataInputs(dataInputs);

        PlatformService platformService = new PlatformService();
        platformService.setServiceDescriptor(platformServiceDescriptor);

        return new JobConfigBuilder(PLATFORM_USER, platformService)
                .withLabel("label")
                .withInputs(multimapInputs)                
                .withParentJob(null)
                .build();
    }

}
