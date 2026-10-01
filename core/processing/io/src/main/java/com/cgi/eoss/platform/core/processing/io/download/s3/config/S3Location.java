package com.cgi.eoss.platform.core.processing.io.download.s3.config;

import java.net.URI;

import lombok.Data;

/**
 * Class that contains the attributes that identify a S3 location
 *
 * @author cantaveneraf
 *
 */
@Data
public class S3Location {
    private URI baseUrl;
    private String bucket;
    private String region = "";
    private String endpoint;
    private boolean pathStyle = true;

    /**
     * Check if the provided URI starts with the base URL of this S3 Location.
     *
     * @param uri
     *            The URI to be checked
     * @return
     *         True if the provided URI starts with the base URL of this S3 Location,
     *         false otherwise
     */
    public boolean baseUrlMatches(URI uri) {
        return uri.toASCIIString().startsWith(getBaseUrlAsString());
    }

    /**
     * Extract the object storage key by dropping the base URL of this
     * S3 Location from the given URI
     *
     * @param uri
     *            The URI from which the object storage key must be extracted
     * @return
     *         An object storage key.
     *         Throws a RuntimeException if the base URI of this S3 Location does not match the provided URI
     */
    public String extractKey(URI uri) {
        final String baseUriString = getBaseUrlAsString();
        final String uriString = uri.toASCIIString();

        if (!baseUrlMatches(uri)) {
            throw new IllegalArgumentException("Can't extract s3 key from uri: " + uriString + " because it does not match the base uri: " + baseUriString);
        }

        return uriString.substring(uriString.indexOf(baseUriString) + baseUriString.length());
    }

    private String getBaseUrlAsString() {
        String uriString = baseUrl.toASCIIString();
        if (!uriString.endsWith("/")) {
            uriString += "/";
        }
        return uriString;
    }
}
