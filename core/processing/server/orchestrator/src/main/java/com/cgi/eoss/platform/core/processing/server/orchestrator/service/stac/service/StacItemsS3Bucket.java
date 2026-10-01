package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

/**
 * <p>
 * Interface that exposes the name of an S3 bucket where Stac Items are put.
 * </p>
 */

public interface StacItemsS3Bucket {

    /**
     * Retrieves the Stac S3 items bucket name.
     * @return the Stac S3 items bucket name.
     */
    String getStacItemsS3BucketName();
}
