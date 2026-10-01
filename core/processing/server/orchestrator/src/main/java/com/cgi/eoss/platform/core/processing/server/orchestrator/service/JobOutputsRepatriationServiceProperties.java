package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

/**
 * Interface that exposes the configuration parameters of the JobOutputsRepatriationService
 *
 */
public interface JobOutputsRepatriationServiceProperties {

    /**
     * Retrieve the job outputs bucket name
     *
     * @return
     *         The job outputs bucket name
     */
    String getJobOutputsBucketName();

}
