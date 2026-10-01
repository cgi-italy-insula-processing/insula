package com.cgi.eoss.platform.core.processing.server.api;

import com.cgi.eoss.platform.core.queues.QueuesCoreTestConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;


@TestConfiguration
@Import({QueuesCoreTestConfig.class})
public class ApiTestConfig {

}