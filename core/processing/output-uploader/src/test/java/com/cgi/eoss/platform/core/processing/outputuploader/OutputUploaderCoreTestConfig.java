package com.cgi.eoss.platform.core.processing.outputuploader;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({OutputUploaderCoreConfig.class, OutputUploaderCoreDefaultConfig.class})
public class OutputUploaderCoreTestConfig {
}
