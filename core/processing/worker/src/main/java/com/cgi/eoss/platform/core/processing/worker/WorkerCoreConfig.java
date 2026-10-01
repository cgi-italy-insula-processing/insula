package com.cgi.eoss.platform.core.processing.worker;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.CoreLegacyWorkflowBuilder;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.KubernetesCoreDispatcher;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.KubernetesWorkerJobUpdatesManager;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowBuilder;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowEventsDispatcher;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowProperties;
import com.cgi.eoss.platform.core.queues.QueuesCoreConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Main configuration class of the worker core application
 */
@Configuration
@ComponentScan(
    basePackageClasses = {WorkerCoreConfig.class},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = PlatformWorkerCoreApplication.class))
@EnableConfigurationProperties(value = {LegacyWorkflowProperties.class})
@Import({
    QueuesCoreConfig.class
})
public class WorkerCoreConfig {

    @Configuration
    @ConditionalOnProperty(name = "platform.worker.app", havingValue = "processing-core", matchIfMissing = false)
    public static class DefaultConfig {

        @Bean
        public KubernetesCoreDispatcher kubernetesCoreDispatcher(
            LegacyWorkflowEventsDispatcher legacyWorkflowEventsDispatcher,
            KubernetesWorkerJobUpdatesManager kubernetesWorkerJobUpdatesManager
        ) {

            return new KubernetesCoreDispatcher(legacyWorkflowEventsDispatcher, kubernetesWorkerJobUpdatesManager);
        }

        /**
         * Creates the legacy workflow builder based on core legacy workflow templates.
         *
         * @param legacyWorkflowProperties configuration properties used to build legacy workflows
         * @return the legacy workflow builder bean
         */
        @Bean
        public LegacyWorkflowBuilder legacyWorkflowBuilder(LegacyWorkflowProperties legacyWorkflowProperties) {
            return new CoreLegacyWorkflowBuilder(legacyWorkflowProperties);
        }
    }

}
