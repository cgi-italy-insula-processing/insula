package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CorePlatformParameterExtractorTest {

    @Test
    public void testGetTimeout_ReturnsConfiguredTimeout_WhenTimeoutIsProvided() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.getInputs().put("timeout", "42");
        Job job = new Job();
        job.setConfig(jobConfig);

        assertThat(CorePlatformParameterExtractor.getTimeout(job)).isEqualTo(42);
    }

    @Test
    public void testGetTimeout_ReturnsZero_WhenTimeoutIsNotProvided() {
        Job job = new Job();
        job.setConfig(new JobConfig());

        assertThat(CorePlatformParameterExtractor.getTimeout(job)).isZero();
    }
}
