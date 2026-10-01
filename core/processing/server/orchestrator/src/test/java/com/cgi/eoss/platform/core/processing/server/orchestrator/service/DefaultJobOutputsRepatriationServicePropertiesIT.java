package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public abstract class DefaultJobOutputsRepatriationServicePropertiesIT {

    @Autowired
    protected JobOutputsRepatriationServiceProperties jobOutputsRepatriationServiceProperties;

    public static class DefaultJobOutputsRepatriationServicePropertiesDefaultValuesIT extends DefaultJobOutputsRepatriationServicePropertiesIT {

        @Test
        public void testDefaultJobOutputsRepatriationServicePropertiesDefaultValues() {
            assertThat(jobOutputsRepatriationServiceProperties.getJobOutputsBucketName()).isEqualTo("jobOutputs-bucket");
        }
    }

    @TestPropertySource(properties = "platform.orchestrator.objectStorage.jobOutputsBucketName=job-outputs-custom")
    public static class DefaultJobOutputsRepatriationServicePropertiesCustomValuesIT extends DefaultJobOutputsRepatriationServicePropertiesIT {

        @Test
        public void testDefaultJobOutputsRepatriationServicePropertiesCustomValues() {
            assertThat(jobOutputsRepatriationServiceProperties.getJobOutputsBucketName()).isEqualTo("job-outputs-custom");
        }
    }
}
