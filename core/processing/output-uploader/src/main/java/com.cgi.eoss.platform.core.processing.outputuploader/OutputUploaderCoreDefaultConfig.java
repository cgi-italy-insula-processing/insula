package com.cgi.eoss.platform.core.processing.outputuploader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

import static com.cgi.eoss.platform.core.processing.outputuploader.OutputUtils.deserializeOutputs;

@Configuration
@EnableConfigurationProperties(value = {DefaultIngestionServiceProperties.class})
public class OutputUploaderCoreDefaultConfig {

    /**
     * Create an instance of DefaultOutputUploaderRunner.
     *
     * @param ingestionService
     *            The {@link IngestionService} bean designed to upload job outputs to the destination target
     * @param basePath
     *            The job outputs base path
     * @param jobId
     *            The job identifier
     * @param outputs
     *            Outputs for current job
     * @return An instance of {@link DefaultOutputUploaderRunner}
     */
    @Bean
    public DefaultOutputUploaderRunner outputUploaderRunner(
            IngestionService ingestionService,
            @Value("${base_path}") Path basePath,
            @Value("${job_id}") String jobId,
            @Value("${outputs:#{null}}") String outputs ) {
        return new DefaultOutputUploaderRunner(ingestionService, basePath, jobId, deserializeOutputs(outputs));
    }
}
