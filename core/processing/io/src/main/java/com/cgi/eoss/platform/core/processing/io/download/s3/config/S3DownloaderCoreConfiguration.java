package com.cgi.eoss.platform.core.processing.io.download.s3.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that holds the configuration of the {@code S3CoreDownloader}
 *
 * @author cantaveneraf
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.io.downloader.s3")
public class S3DownloaderCoreConfiguration {

    protected List<S3AccountProperties> accounts = new ArrayList<>();

    /**
     * Obtain the account properties associated to the given URI
     *
     * @param uri
     *            The URI for which the account properties should be returned
     * @return
     *         The matching account properties.
     *         A runtime exception will be thrown if the URI doesn't match any account property.
     */
    public S3AccountProperties getAccountProperties(URI uri) {
        return accounts.stream()
            .filter(a -> a.containsLocation(uri))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Can't find S3 configuration for uri: " + uri));
    }

}
