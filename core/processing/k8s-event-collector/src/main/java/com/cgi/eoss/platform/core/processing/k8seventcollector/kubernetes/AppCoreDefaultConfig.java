package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import com.cgi.eoss.platform.core.queues.QueuesCoreBaseConfig;
import com.cgi.eoss.platform.core.queues.QueuesCoreDefaultConfig;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

@Configuration
@Import({QueuesCoreDefaultConfig.class, QueuesCoreBaseConfig.class})
@ConditionalOnProperty(
        name = "platform.k8seventcollector.app",
        havingValue = "processing-core",
        matchIfMissing = false )
public class AppCoreDefaultConfig {

    @Autowired
    private QueueService queueService;

    @Autowired
    private List<PodEventHandler> podEventHandlers;

    /**
     * Creates the {@link PodEventProcessor} bean used to process collected pod events.
     *
     * @return a configured {@link PodEventProcessor} instance
     */
    @Bean
    public PodEventProcessor podEventProcessor() {
        return new PodEventProcessor(podEventHandlers);
    }

    /**
     * Creates the {@link WorkflowEventProcessor} bean used to process collected workflow events.
     *
     * @return a configured {@link WorkflowEventProcessor} instance
     */
    @Bean
    public WorkflowEventProcessor workflowEventProcessor() {
        return new WorkflowEventProcessor(queueService);
    }

}
