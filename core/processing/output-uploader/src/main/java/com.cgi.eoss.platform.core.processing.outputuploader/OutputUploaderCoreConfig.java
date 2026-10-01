package com.cgi.eoss.platform.core.processing.outputuploader;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class OutputUploaderCoreConfig {

    /**
     * Creates a configured {@link IngestionService} bean.
     *
     * @param ingestionServiceProperties configuration properties for the ingestion service
     * @param amazonS3                   S3 client used for storage operations
     * @param transferManager            manager used to handle file transfers to/from S3
     * @param stageOutService            service used to stage out processed data
     * @param executorService            executor used to run ingestion tasks asynchronously
     * @return the configured {@code  IngestionService} bean.
     */
    @Bean
    public IngestionService ingestionService(
            IngestionServiceProperties ingestionServiceProperties,
            AmazonS3 amazonS3,
            TransferManager transferManager,
            StageOutService stageOutService,
            ExecutorService executorService
    ) {
        return new IngestionService(
                ingestionServiceProperties,
                amazonS3,
                transferManager,
                stageOutService,
                executorService);
    }

    /**
     * Creates a configured {@link StageOutService} bean.
     * @param fileSizeByteThreshold the maximum size of a STAC catalog/item files allowed.
     * @return The configured {@code stageOutService} bean.
     * */
    @Bean
    public StageOutService stageOutService(@Value("${platform.output.uploader.stage-out.catalogFilesizeThresholdBytes:1000000}") long fileSizeByteThreshold) {
        return new StageOutService(fileSizeByteThreshold);
    }

    /**
     * Creates a configured {@link AmazonS3} bean.
     *
     * @param retries            maximum number of retry attempts on error
     * @param connectionTimeout  connection timeout in milliseconds
     * @param socketTimeout      socket timeout in milliseconds
     * @param protocol           protocol to use ("HTTP" or "HTTPS")
     * @param accessKey          access key for AWS authentication
     * @param secretKey          secret key for AWS authentication
     * @param endpoint           S3 service endpoint
     * @param region             AWS region of the service
     * @return the configured {@code  AmazonS3} bean.
     */
    @Bean
    public AmazonS3 amazonS3(
            @Value("${retries:3}") int retries,
            @Value("${connectionTimeout:10000}") int connectionTimeout,
            @Value("${socketTimeout:10000}") int socketTimeout,
            @Value("${protocol:HTTP}") String protocol,
            @Value("${accessKey:access}") String accessKey,
            @Value("${secretKey:secret}") String secretKey,
            @Value("${endpoint:endpoint}") String endpoint,
            @Value("${region:region}") String region) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");
        clientConfiguration.setMaxErrorRetry(retries);
        clientConfiguration.setConnectionTimeout(connectionTimeout);
        clientConfiguration.setSocketTimeout(socketTimeout);
        clientConfiguration.setProtocol(protocol.equals("HTTP") ? Protocol.HTTP : Protocol.HTTPS);
        return AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .build();
    }

    /**
     * Creates and configures {@link TransferManager} bean.
     * The TransferManager provides a high-level API for managing uploads and downloads
     *
     * @param amazonS3 the Amazon S3 client to use with the TransferManager
     * @return a configured {@code transferManager} bean.
     */
    @Bean
    public TransferManager transferManager(AmazonS3 amazonS3) {
        return TransferManagerBuilder.standard()
                .withS3Client(amazonS3).build();
    }

    /**
     * Creates and configures a {@link ExecutorService} bean used for upload of files in parallel.
     *
     * @param executorMaxThreads the maximum number of threads in the pool.
     * @return a fixed thread pool executor.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService executorService(@Value("${platform.output.uploader.executor.maxThreads:5}") int executorMaxThreads) {
        return Executors.newFixedThreadPool(executorMaxThreads);
    }
}
