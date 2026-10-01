package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import java.util.List;

/**
 * Configuration properties used by {@link StacInputsService}.
 */
public interface StacInputsServiceProperties {

    /**
     * Retrieves the maximum accepted size, in bytes, of a STAC document downloaded from an external service.
     *
     * @return the maximum accepted STAC document size
     */
    int getMaxDocumentSize();

    /**
     * Retrieves the Insula base URLs
     *
     * @return the Insula base URLs
     */
    List<String> getInsulaBaseUrls();

}
