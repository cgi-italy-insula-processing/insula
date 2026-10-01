package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.ImmutableList;

@RunWith(SpringRunner.class)
@EnableConfigurationProperties(DefaultStacInputsServiceProperties.class)
public abstract class StacInputsServicePropertiesIT {

    @Autowired
    protected StacInputsServiceProperties stacInputsServiceProperties;

    public static class StacInputsServicePropertiesDefaultValuesIT extends StacInputsServicePropertiesIT {

        @Test
        public void testStacInputsServicePropertiesDefaultValues() {
            assertThat(stacInputsServiceProperties.getMaxDocumentSize()).isEqualTo(1024*100);
            assertThat(stacInputsServiceProperties.getInsulaBaseUrls()).isEqualTo(
                    Collections.singletonList("http://platform"));
        }
    }

    @TestPropertySource(properties = {
            "platform.orchestrator.stac.inputs.service.maxDocumentSize=100",
            "platform.orchestrator.stac.inputs.service.insulaBaseUrls=https://insula.earth,https://platform"
    })
    public static class StacInputsServicePropertiesCustomValuesIT extends StacInputsServicePropertiesIT {

        @Test
        public void testStacInputsServicePropertiesCustomValues() {
            assertThat(stacInputsServiceProperties.getMaxDocumentSize()).isEqualTo(100);
            assertThat(stacInputsServiceProperties.getInsulaBaseUrls()).isEqualTo(
                    ImmutableList.of("https://insula.earth", "https://platform"));
        }
    }
}