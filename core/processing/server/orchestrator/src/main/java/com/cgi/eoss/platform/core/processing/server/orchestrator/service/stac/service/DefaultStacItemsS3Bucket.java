package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <p>Default implementation of {@link StacItemsS3Bucket} that provides the S3 bucket name storing
 * information about Stac Items.</p>
 */

@AllArgsConstructor
@Getter
public class DefaultStacItemsS3Bucket implements StacItemsS3Bucket {

    private final String stacItemsS3BucketName;
}
