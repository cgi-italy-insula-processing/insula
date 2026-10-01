package com.cgi.eoss.platform.core.processing.io.download.s3.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Account properties to configure an S3 Downloader
 *
 * @author cantaveneraf
 *
 */
@Data
public class S3AccountProperties {

    private List<S3Location> locations = new ArrayList<>();
    private String accessKey = "";
    private String privateKey = "";
    private int retries = 0;
    private int connectionTimeout = 10000;
    private int socketTimeout = 10000;

    /**
     * Check if there is a S3Location that matches the given URI
     *
     * @param uri
     *            The URI that should match with the S3 Location
     *
     * @return
     *         True if a matching is found, false otherwise
     */
    public boolean containsLocation(URI uri) {
        return locations.stream()
                .anyMatch(l -> l.baseUrlMatches(uri));
    }

    /**
     * Retrieves the S3Location that matches the given URI
     *
     * @param uri
     *            The URI that should match with the S3Location
     * @return
     *         An instance of the matching S3Location if it is found.
     *         Throws a runtime exception if the S3Location is not found
     */
    public S3Location getLocation(URI uri) {
        return locations.stream()
                .filter(l -> l.baseUrlMatches(uri))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Failed to retrieve S3 Location for uri: " + uri));
    }

}
