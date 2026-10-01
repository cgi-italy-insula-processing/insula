package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Class that holds all the properties related to the Api Client.
 */

@Data
@ConfigurationProperties("platform.kubernetes.api.client")
public class ApiClientProperties {

    private int readTimeoutSeconds = 60;
    private int connectionTimeoutSeconds = 40;
    private int writeTimeoutSeconds = 50;
    private int watchTimeoutSeconds = 50;

}