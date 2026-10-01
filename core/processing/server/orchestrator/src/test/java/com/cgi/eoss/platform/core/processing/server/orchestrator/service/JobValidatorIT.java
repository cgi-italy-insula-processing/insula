package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.utils.JobConfigBuilder;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobValidationResult;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class })
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class JobValidatorIT {

    private static final User PLATFORM_USER = new User("platformUser");

    @Autowired
    private JobValidator jobValidator;

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenJobInputsAreValid() {

        Job job = buildJob(ArrayListMultimap.create(), ImmutableList.of(literalStringInput("string_input")));
        job.getConfig().getInputs().put("string_input", "value");

        JobValidationResult validationResult = jobValidator.validate(
                job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsInvalidValidationResult_WhenJobContainsInputNotDeclaredInServiceDescriptor() {

        Multimap<String, String> inputs = ArrayListMultimap.create();
        inputs.put("string_input", "value");
        inputs.put("undeclared_input", "value");
        Job job = buildJob(inputs, ImmutableList.of(literalStringInput("string_input")));

        JobValidationResult validationResult = jobValidator.validate(
                job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Missing required input(s): undeclared_input");
    }

    @Test
    public void testValidate_ReturnsInvalidValidationResult_WhenInputValueDoesNotMatchType() {

        Multimap<String, String> inputs = ArrayListMultimap.create();
        inputs.put("int_input", "notANumber");
        Job job = buildJob(inputs, ImmutableList.of(PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id("int_input").defaultAttrs(ImmutableMap.of("dataType", "integer"))
                .minOccurs(1).maxOccurs(1).build()));

        JobValidationResult validationResult = jobValidator.validate(
                job, new JobInputs(job.getExtId(), PLATFORM_USER.getName(), job.getConfig()));

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Value does not match type for input(s): int_input");
    }

    private static PlatformServiceDescriptor.Parameter literalStringInput(String id) {
        return PlatformServiceDescriptor.Parameter.builder()
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                .id(id).defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of("format", "OTHER", "preventUrlDownload", "true"))
                .minOccurs(1).maxOccurs(1).build();
    }

    private static Job buildJob(Multimap<String, String> inputs, List<PlatformServiceDescriptor.Parameter> dataInputs) {
        PlatformServiceDescriptor platformServiceDescriptor = new PlatformServiceDescriptor();
        platformServiceDescriptor.setDataInputs(dataInputs);

        PlatformService platformService = new PlatformService();
        platformService.setServiceDescriptor(platformServiceDescriptor);

        JobConfig jobConfig = new JobConfigBuilder(PLATFORM_USER, platformService)
                .withLabel("label")
                .withInputs(inputs)
                .withParentJob(null)
                .build();

        Job job = new Job();
        job.setOwner(jobConfig.getOwner());
        job.setConfig(jobConfig);
        return job;
    }
}
