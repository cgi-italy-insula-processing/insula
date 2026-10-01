package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default implementation of {@link StacInputsServiceProperties} backed by configured STAC input service
 * property values.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("platform.orchestrator.stac.inputs.service")
public class DefaultStacInputsServiceProperties implements StacInputsServiceProperties {

    private static final int DEFAULT_MAX_DOCUMENT_SIZE = 1024 * 100;

    private int maxDocumentSize = DEFAULT_MAX_DOCUMENT_SIZE;

    private List<String> insulaBaseUrls = Collections.singletonList("http://platform");

}
