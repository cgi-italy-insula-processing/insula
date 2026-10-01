package com.cgi.eoss.platform.core.processing.worker;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.CoreLegacyWorkflowBuilder;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.KubernetesCoreDispatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {WorkerCoreConfig.class, WorkerCoreTestConfig.class})
@TestPropertySource(locations = {"classpath:test-worker-core-k8.properties"})
public abstract class WorkerCoreConfigIT {

    @Autowired
    protected ApplicationContext applicationContext;

    public static class DefaultConfigIT extends WorkerCoreConfigIT {

        @Test
        public void testDefaultConfig_CreatesCoreDefaultBeans_WhenWorkerAppIsProcessingCore() {
            // Check that the beans are created
            assertThat(applicationContext.getBean("kubernetesCoreDispatcher"))
                .isInstanceOf(KubernetesCoreDispatcher.class);

            assertThat(applicationContext.getBean("legacyWorkflowBuilder"))
                .isInstanceOf(CoreLegacyWorkflowBuilder.class);
        }
    }
}
