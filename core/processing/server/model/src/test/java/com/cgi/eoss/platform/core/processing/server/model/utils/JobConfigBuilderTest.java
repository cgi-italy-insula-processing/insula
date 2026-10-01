package com.cgi.eoss.platform.core.processing.server.model.utils;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class JobConfigBuilderTest {

    User owner = ProcessingCoreEntities.createUser().build();
    PlatformService service = ProcessingCoreEntities.createPlatformService(owner).build();
    private JobConfigBuilder jobConfigBuilder;

    @Before
    public void setUp() {
        jobConfigBuilder = new JobConfigBuilder(owner, service);
    }

    @Test
    public void testBuild_BuildsJobConfigWithLabel_WhenLabelIsSet() {
        JobConfig jobConfig = jobConfigBuilder.withLabel("someLabel").build();

        assertThat(jobConfig.getLabel()).isEqualTo("someLabel");

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithNullLabel_WhenProvidedLabelIsEmpty() {
        JobConfig jobConfig = jobConfigBuilder.withLabel("").build();

        assertThat(jobConfig.getLabel()).isNull();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithNullLabel_WhenProvidedLabelIsNull() {
        JobConfig jobConfig = jobConfigBuilder.withLabel(null).build();

        assertThat(jobConfig.getLabel()).isNull();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithSystematicParameter_WhenSystematicParameterIsSet() {
        JobConfig jobConfig = jobConfigBuilder.withSystematicParameter("someSystematicParameter").build();

        assertThat(jobConfig.getSystematicParameter()).isEqualTo("someSystematicParameter");

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithEmptySystematicParameter_WhenSystematicParameterIsEmpty() {
        JobConfig jobConfig = jobConfigBuilder.withSystematicParameter("").build();

        assertThat(jobConfig.getSystematicParameter()).isEqualTo("");

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithNullSystematicParameter_WhenProvidedSystematicParameterIsNull() {
        JobConfig jobConfig = jobConfigBuilder.withSystematicParameter(null).build();

        assertThat(jobConfig.getSystematicParameter()).isNull();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithParentJob_WhenParentJobIsSet() {

        Job parentJob = ProcessingCoreEntities.createJob(owner, new JobConfig(owner, service)).build();
        JobConfig jobConfig = jobConfigBuilder.withParentJob(parentJob).build();

        assertThat(jobConfig.getParent()).isEqualTo(parentJob);

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithNullParentJob_WhenProvidedParentJobIsNull() {
        JobConfig jobConfig = jobConfigBuilder.withParentJob(null).build();

        assertThat(jobConfig.getParent()).isNull();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithInputs_WhenInputsIsSet() {
        Multimap<String, String> inputs = ArrayListMultimap.create();
        inputs.put("someKey", "someValue");

        JobConfig jobConfig = jobConfigBuilder.withInputs(inputs).build();

        assertThat(jobConfig.getInputs()).isEqualTo(inputs);

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithNullInputs_WhenProvidedInputsIsNull() {
        JobConfig jobConfig = jobConfigBuilder.withInputs(null).build();

        assertThat(jobConfig.getInputs()).isNull();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigWithOnlyOwnerAndService_WhenNoOtherParameterIsInitialized() {
        JobConfig jobConfig = jobConfigBuilder.build();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);

        assertThat(jobConfig.getLabel()).isNull();
        assertThat(jobConfig.getSystematicParameter()).isNull();
        assertThat(jobConfig.getInputs()).isNull();
        assertThat(jobConfig.getParent()).isNull();
    }

    @Test
    public void testBuild_BuildsJobConfigInitializedWithAllFields_WhenAllFieldsAreSet() {
        Job parentJob = ProcessingCoreEntities.createJob(owner, new JobConfig(owner, service)).build();
        Multimap<String, String> inputs = ArrayListMultimap.create();
        inputs.put("someKey", "someValue");

        JobConfig jobConfig = jobConfigBuilder
                .withLabel("someLabel")
                .withSystematicParameter("someSystematicParameter")
                .withParentJob(parentJob)
                .withInputs(inputs)
                .build();

        assertThat(jobConfig.getService()).isEqualTo(service);
        assertThat(jobConfig.getOwner()).isEqualTo(owner);
        assertThat(jobConfig.getLabel()).isEqualTo("someLabel");
        assertThat(jobConfig.getSystematicParameter()).isEqualTo("someSystematicParameter");
        assertThat(jobConfig.getParent()).isEqualTo(parentJob);
        assertThat(jobConfig.getInputs()).isEqualTo(inputs);
    }
}