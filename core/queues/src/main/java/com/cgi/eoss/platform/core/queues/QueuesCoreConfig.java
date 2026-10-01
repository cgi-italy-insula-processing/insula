package com.cgi.eoss.platform.core.queues;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Automatically scans and configures all the spring beans of the Platform Queues Core module.
 * </p>
 */
@Configuration
@ComponentScan( basePackageClasses = { QueuesCoreConfig.class })
public class QueuesCoreConfig {
}
