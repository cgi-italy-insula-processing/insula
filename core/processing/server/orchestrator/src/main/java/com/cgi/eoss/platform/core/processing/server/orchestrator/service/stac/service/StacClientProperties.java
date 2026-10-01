package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Global configuration properties used by the STAC input service and its HTTP client.
 */
@Data
@ConfigurationProperties("platform.orchestrator.stac.inputs.service")
public class StacClientProperties {

    private int connectionTimeoutSeconds = 30;

    private int readTimeoutSeconds = 30;

    private int writeTimeoutSeconds = 30;

    private int callTimeoutSeconds = 40;

    private String ogcapiPath = "/ogcapi";

    private int maxItemsStacSearch = 500;

}
