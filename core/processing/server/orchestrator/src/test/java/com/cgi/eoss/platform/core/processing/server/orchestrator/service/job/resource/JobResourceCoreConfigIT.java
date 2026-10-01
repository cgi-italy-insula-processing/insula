package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource;

import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {JobResourceCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public abstract class JobResourceCoreConfigIT {

    @Autowired
    protected ApplicationContext applicationContext;

    public static class DefaultConfigurationIT extends JobResourceCoreConfigIT {

        @Test
        public void testJobResourceCoreConfig_CreatesJobResourceBean_WhenPropertyIsMissing() {
            Object bean = applicationContext.getBean("jobResourceManagementService");
            assertThat(bean).isInstanceOf(DefaultJobResourceManagementServiceImpl.class);
        }
    }

    @TestPropertySource(properties = "platform.orchestrator.jobResourceManagementService=default")
    public static class CustomConfigurationIT extends JobResourceCoreConfigIT {

        @Test
        public void testJobResourceCoreConfig_CreatesJobResourceBean_WhenPropertyIsConfigured() {
            Object bean = applicationContext.getBean("jobResourceManagementService");
            assertThat(bean).isInstanceOf(DefaultJobResourceManagementServiceImpl.class);
        }
    }

    @TestPropertySource(properties = "platform.orchestrator.jobResourceManagementService=someOtherValue")
    public static class NonDefaultJobResourceConfigIT extends JobResourceCoreConfigIT {

        @Test
        public void testJobResourceCoreConfig_DoesNotCreateBean_WhenPropertyIsNotDefault() {
            assertThatThrownBy(() -> applicationContext.getBean("jobResourceManagementService"))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }
}