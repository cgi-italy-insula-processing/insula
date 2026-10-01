package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Class that holds CWL Service HTTP Client configuration properties.
 */
@Data
@ConfigurationProperties("platform.orchestrator.cwl.client")
public class CwlServiceProperties {

    private int maxDocumentSize = 1024 * 100;

    private int connectionTimeoutSeconds = 30;

    private int readTimeoutSeconds = 30;

    private int writeTimeoutSeconds = 30;

    private int callTimeoutSeconds = 40;
}
