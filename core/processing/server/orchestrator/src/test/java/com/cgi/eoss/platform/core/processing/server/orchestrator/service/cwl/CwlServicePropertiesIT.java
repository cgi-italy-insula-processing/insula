package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@EnableConfigurationProperties(CwlServiceProperties.class)
public abstract class CwlServicePropertiesIT {

    @Autowired
    protected CwlServiceProperties cwlServiceProperties;

    public static class CwlServicePropertiesDefaultValuesIT extends CwlServicePropertiesIT {

        @Test
        public void testCwlPropertiesDefaultValues() {
            assertThat(cwlServiceProperties.getMaxDocumentSize()).isEqualTo(1024*100);
            assertThat(cwlServiceProperties.getConnectionTimeoutSeconds()).isEqualTo(30);
            assertThat(cwlServiceProperties.getReadTimeoutSeconds()).isEqualTo(30);
            assertThat(cwlServiceProperties.getWriteTimeoutSeconds()).isEqualTo(30);
            assertThat(cwlServiceProperties.getCallTimeoutSeconds()).isEqualTo(40);
        }
    }

    @TestPropertySource(properties = {
            "platform.orchestrator.cwl.client.maxDocumentSize=100",
            "platform.orchestrator.cwl.client.connectionTimeoutSeconds=50",
            "platform.orchestrator.cwl.client.readTimeoutSeconds=40",
            "platform.orchestrator.cwl.client.writeTimeoutSeconds=20",
            "platform.orchestrator.cwl.client.callTimeoutSeconds=60"
    })
    public static class CwlServicePropertiesCustomValuesIT extends CwlServicePropertiesIT {

        @Test
        public void testCwlPropertiesCustomValues() {
            assertThat(cwlServiceProperties.getMaxDocumentSize()).isEqualTo(100);
            assertThat(cwlServiceProperties.getConnectionTimeoutSeconds()).isEqualTo(50);
            assertThat(cwlServiceProperties.getReadTimeoutSeconds()).isEqualTo(40);
            assertThat(cwlServiceProperties.getWriteTimeoutSeconds()).isEqualTo(20);
            assertThat(cwlServiceProperties.getCallTimeoutSeconds()).isEqualTo(60);
        }
    }
}
