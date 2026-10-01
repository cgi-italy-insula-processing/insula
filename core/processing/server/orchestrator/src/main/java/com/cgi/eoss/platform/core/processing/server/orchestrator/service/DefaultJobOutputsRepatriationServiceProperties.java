package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import lombok.Builder;
import lombok.Value;

/**
 * Default implementation of the JobOutputsRepatriationServiceProperties that retrieves the
 * PlatformWorkerJobUpdatesManager configuration properties from internal attributes.
 *
 * @author cantaveneraf
 *
 */
@Value
@Builder
public class DefaultJobOutputsRepatriationServiceProperties implements JobOutputsRepatriationServiceProperties {

    private String jobOutputsBucketName;

}
