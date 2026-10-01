package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core configuration for job resource management beans.
 */
@Configuration
public class JobResourceCoreConfig {

    /**
     * Creates the default JobResourceManagementService bean.
     * Sets resources based on Service resource and Default UsageType.
     *
     * @return Configured DefaultJobResourceManagementServiceImpl instance
     */
    @ConditionalOnProperty(name = "platform.orchestrator.jobResourceManagementService", havingValue = "default", matchIfMissing = true)
    @Bean("jobResourceManagementService")
    public JobResourceManagementService defaultJobResourceManagementService() {
        return new DefaultJobResourceManagementServiceImpl();
    }
}