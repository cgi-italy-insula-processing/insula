package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(SpringRunner.class)
@EnableConfigurationProperties(ApiClientProperties.class)
public abstract class ApiClientPropertiesIT {

    @Autowired
    protected ApiClientProperties apiClientProperties;

    public static class ApiClientPropertiesDefaultValuesIT extends ApiClientPropertiesIT {

        @Test
        public void testApiClientProperties_InjectsDefaultPropertyValues_WhenApiClientPropertiesAreNotProvided() {
            assertThat(apiClientProperties.getConnectionTimeoutSeconds()).isEqualTo(40);
            assertThat(apiClientProperties.getReadTimeoutSeconds()).isEqualTo(60);
            assertThat(apiClientProperties.getWriteTimeoutSeconds()).isEqualTo(50);
            assertThat(apiClientProperties.getWatchTimeoutSeconds()).isEqualTo(50);
        }
    }

    @TestPropertySource(properties = {
            "platform.kubernetes.api.client.connectionTimeoutSeconds=2",
            "platform.kubernetes.api.client.readTimeoutSeconds=4",
            "platform.kubernetes.api.client.writeTimeoutSeconds=6",
            "platform.kubernetes.api.client.watchTimeoutSeconds=8"
    })
    public static class ApiClientPropertiesCustomValuesIT extends ApiClientPropertiesIT {

        @Test
        public void testApiClientProperties_InjectProvidedValues_WhenApiClientPropertyValuesAreProvided() {
            assertThat(apiClientProperties.getConnectionTimeoutSeconds()).isEqualTo(2);
            assertThat(apiClientProperties.getReadTimeoutSeconds()).isEqualTo(4);
            assertThat(apiClientProperties.getWriteTimeoutSeconds()).isEqualTo(6);
            assertThat(apiClientProperties.getWatchTimeoutSeconds()).isEqualTo(8);
        }
    }
}