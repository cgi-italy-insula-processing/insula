package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.processing.k8seventcollector.AppCoreTestConfig;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import io.kubernetes.client.openapi.ApiClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { AppCoreTestConfig.class, AppCoreConfig.class })
@TestPropertySource(locations = {"classpath:test-k8-event-collector-core.properties"})
public class AppCoreConfigIT {

    @Autowired
    protected ApplicationContext context;

    @Test
    public void testAppCoreConfig_CreatesScheduledTreadPoolBean() {
        String[] scheduledThreadPool = context.getBeanNamesForType(ScheduledExecutorService.class);
        assertThat(scheduledThreadPool).hasSize(1);
        assertThat(scheduledThreadPool[0]).isEqualTo("scheduledThreadPool");
    }

    @Test
    public void testAppCoreConfig_CreatesLegacyWorkflowPodEventHandlerBean() {
        String[] legacyWorkflowPodEventHandler = context.getBeanNamesForType(LegacyWorkflowPodEventHandler.class);
        assertThat(legacyWorkflowPodEventHandler).hasSize(1);
        assertThat(legacyWorkflowPodEventHandler[0]).isEqualTo("legacyWorkflowPodEventHandler");
    }

    @Test
    public void testAppCoreConfig_CreatesPodEventCollectorBean() {
        String[] podEventCollector = context.getBeanNamesForType(PodEventCollector.class);
        assertThat(podEventCollector).hasSize(1);
        assertThat(podEventCollector[0]).isEqualTo("podEventCollector");
    }

    @Test
    public void testAppCoreConfig_CreatesPodAsyncEventCollectorBean() {
        assertThat(context.getBean("podAsyncEventCollector", KubernetesAsyncEventCollector.class))
                .isNotNull();
    }

    @Test
    public void testAppCoreConfig_CreatesWorkflowAsyncEventCollectorBean() {
        assertThat(context.getBean("workflowAsyncEventCollector", KubernetesAsyncEventCollector.class))
                .isNotNull();
    }

    @Test
    public void testAppCoreConfig_CreatesEventCollectorRunnerBean() {
        String[] eventCollectorRunner = context.getBeanNamesForType(EventCollectorRunner.class);
        assertThat(eventCollectorRunner).hasSize(1);
        assertThat(eventCollectorRunner[0]).isEqualTo("eventCollectorRunner");
    }

    @Test
    public void testAppCoreConfig_CreatesKubernetesEventCollectorManagerBean() {
        String[] kubernetesEventCollectorManager = context.getBeanNamesForType(KubernetesEventCollectorManager.class);
        assertThat(kubernetesEventCollectorManager).hasSize(1);
        assertThat(kubernetesEventCollectorManager[0]).isEqualTo("kubernetesEventCollectorManager");
    }

    @Test
    public void testAppCoreConfig_CreatesPodApiClientBean() {
        assertThat(context.getBean("podApiClient", ApiClient.class)).isNotNull();
    }

    @Test
    public void testAppCoreConfig_CreatesWorkflowApiClientBean() {
        assertThat(context.getBean("workflowApiClient", ApiClient.class)).isNotNull();
    }

    @Test
    public void testAppCoreConfig_CreatesWorkflowEventCollectorBean() {
        String[] workflowEventCollector = context.getBeanNamesForType(WorkflowEventCollector.class);
        assertThat(workflowEventCollector).hasSize(1);
        assertThat(workflowEventCollector[0]).isEqualTo("workflowEventCollector");
    }

}