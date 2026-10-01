package com.cgi.eoss.platform.core.processing.outputuploader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = {OutputUploaderCoreTestConfig.class})
public abstract class DefaultIngestionServicePropertiesIT {

    @Autowired
    protected DefaultIngestionServiceProperties ingestionServiceProperties;

    public static class DefaultIngestionServicePropertiesDefaultValuesIT extends DefaultIngestionServicePropertiesIT {

        @Test
        public void testDefaultIngestionServicePropertiesDefaultValues() {
            assertThat(ingestionServiceProperties.getJobOutputsBucketName()).isEqualTo("job-outputs");
        }
    }

    @TestPropertySource(properties = "jobOutputsBucketName=job-outputs-custom")
    public static class DefaultIngestionServicePropertiesCustomValuesIT extends DefaultIngestionServicePropertiesIT {

        @Test
        public void testDefaultIngestionServicePropertiesCustomValues() {
            assertThat(ingestionServiceProperties.getJobOutputsBucketName()).isEqualTo("job-outputs-custom");
        }
    }
}
