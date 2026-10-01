package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Core configuration for STAC input processing. Declares the STAC HTTP client, the HTTP STAC downloader, the
 * {@link StacInputsService}, the ordered STAC downloader list and the STAC items object-storage bucket.
 */
@Configuration
@EnableConfigurationProperties(StacClientProperties.class)
@Log4j2
public class StacInputsServiceCoreConfiguration {

    /**
     * Instantiate a bean of type OkHttpClient to perform HTTP requests.
     *
     * @param stacClientProperties the STAC HTTP client properties
     * @return an OkHttpClient object to be injected in the STAC inputs service
     */
    @Bean
    public OkHttpClient stacClient(StacClientProperties stacClientProperties) {
        return new OkHttpClient().newBuilder()
                .connectTimeout(stacClientProperties.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(stacClientProperties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(stacClientProperties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .callTimeout(stacClientProperties.getCallTimeoutSeconds(), TimeUnit.SECONDS)
                .addInterceptor(new HttpLoggingInterceptor(LOG::trace).setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();
    }

    /**
     * Instantiate the {@link HttpStacDownloader}, able to download STAC Documents over HTTP and HTTPS.
     *
     * @param stacClient the OkHttpClient used to perform the requests
     * @param objectMapper the ObjectMapper used to deserialize the response
     * @param stacInputsServiceProperties the STAC inputs service properties
     * @return the HttpStacDownloader bean
     */
    @Bean
    public HttpStacDownloader httpStacDownloader(OkHttpClient stacClient,
                                                 ObjectMapper objectMapper,
                                                 StacInputsServiceProperties stacInputsServiceProperties) {
        return new HttpStacDownloader(stacClient, objectMapper, stacInputsServiceProperties);
    }

    /**
     * Instantiate the {@link StacInputsService} that processes STAC-typed job inputs.
     *
     * @param objectMapper the ObjectMapper used to serialize the STAC documents stored in the object storage
     * @param amazonS3 the object-storage client used to store the STAC documents
     * @param stacItemsS3Bucket the object-storage bucket the STAC documents are stored in
     * @param stacDownloaders the ordered STAC downloaders used to resolve the STAC documents
     * @return the StacInputsService bean
     */
    @Bean
    public StacInputsService stacInputsService(ObjectMapper objectMapper, AmazonS3 amazonS3,
                                               StacItemsS3Bucket stacItemsS3Bucket,
                                               List<StacDownloader> stacDownloaders) {
        return new StacInputsService(objectMapper, amazonS3, stacItemsS3Bucket, stacDownloaders);
    }

    /**
     * Assembles the ordered list of {@link StacDownloader} used to resolve STAC Documents.
     *
     * @param httpStacDownloader the downloader handling STAC Documents over HTTP and HTTPS protocols
     * @return the ordered list of downloaders
     */
    @Bean
    @ConditionalOnProperty(name = "platform.server.app", havingValue = "processing-core", matchIfMissing = false)
    public List<StacDownloader> stacDownloaders(HttpStacDownloader httpStacDownloader) {
        return ImmutableList.of(httpStacDownloader);
    }

    /**
     * Creates the STAC items object-storage bucket holder.
     *
     * @param stacItemsS3BucketName the name of the object-storage bucket the STAC documents are stored in
     * @return the STAC items bucket holder
     */
    @Bean
    @ConditionalOnProperty(name = "platform.server.app", havingValue = "processing-core", matchIfMissing = false)
    public DefaultStacItemsS3Bucket stacItemsS3Bucket(
            @Value("${platform.orchestrator.objectStorage.stacItemsS3BucketName:stac-items}") String stacItemsS3BucketName) {
        return new DefaultStacItemsS3Bucket(stacItemsS3BucketName);
    }

    /**
     * Enables the default STAC input service properties.
     */
    @Configuration
    @EnableConfigurationProperties(DefaultStacInputsServiceProperties.class)
    @ConditionalOnProperty(name = "platform.server.app", havingValue = "processing-core", matchIfMissing = false)
    public static class ProcessingCoreConfig {
    }

}
