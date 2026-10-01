package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.processing.k8seventcollector.AppCoreTestConfig;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { AppCoreTestConfig.class, AppCoreConfig.class })
@TestPropertySource(locations = {"classpath:test-k8-event-collector-core.properties"})
public class AppCoreDefaultConfigIT {

    @Autowired
    private ApplicationContext context;

    @Test
    public void testAppCoreConfig_CreatesWorkflowEventProcessorBean() {
        String[] workflowEventProcessor = context.getBeanNamesForType(WorkflowEventProcessor.class);
        assertThat(workflowEventProcessor).hasSize(1);
        assertThat(workflowEventProcessor[0]).isEqualTo("workflowEventProcessor");
    }

    @Test
    public void testAppCoreConfig_CreatesPodEventProcessorBean() {
        String[] podEventProcessor = context.getBeanNamesForType(PodEventProcessor.class);
        assertThat(podEventProcessor).hasSize(1);
        assertThat(podEventProcessor[0]).isEqualTo("podEventProcessor");
    }

    @Test
    public void testAppCoreConfig_ImportsQueueServiceBean() {
        String[] queueService = context.getBeanNamesForType(QueueService.class);
        assertThat(queueService).hasSize(1);
        assertThat(queueService[0]).isEqualTo("queueService");
    }
}
