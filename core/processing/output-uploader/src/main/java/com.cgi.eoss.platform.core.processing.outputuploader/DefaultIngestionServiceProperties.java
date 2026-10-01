package com.cgi.eoss.platform.core.processing.outputuploader;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default implementation of the IngestionServiceProperties that retrieves the
 * IngestionService configuration properties from internal attributes.
 *
 * @author cantaveneraf
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties
public class DefaultIngestionServiceProperties implements IngestionServiceProperties {

    private String jobOutputsBucketName = "job-outputs";

}
