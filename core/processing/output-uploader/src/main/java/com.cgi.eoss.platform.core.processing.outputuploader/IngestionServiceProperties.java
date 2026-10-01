package com.cgi.eoss.platform.core.processing.outputuploader;

/**
 * Interface that exposes the configuration parameters of the IngestionService
 *
 */
public interface IngestionServiceProperties {

    /**
     * Retrieve the job outputs bucket name
     *
     * @return
     *         The job outputs bucket name
     */
    String getJobOutputsBucketName();

}
