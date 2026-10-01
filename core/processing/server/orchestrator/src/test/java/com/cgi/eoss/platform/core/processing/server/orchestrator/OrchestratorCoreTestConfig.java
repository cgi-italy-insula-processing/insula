package com.cgi.eoss.platform.core.processing.server.orchestrator;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import com.cgi.eoss.platform.core.queues.QueuesCoreTestConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({QueuesCoreTestConfig.class})
public class OrchestratorCoreTestConfig {
}
