package com.cgi.eoss.platform.core.processing.io.download.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.cgi.eoss.platform.core.processing.io.download.Downloader;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3AccountProperties;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3DownloaderCoreConfiguration;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3Location;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Downloader implementation that handles downloads from S3 object storages
 */
@Log4j2
@Component
@ConditionalOnProperty(value = "platform.core.io.downloader.s3.enabled", havingValue = "true")
public class S3CoreDownloader implements Downloader {

    private static final Set<String> PROTOCOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("http", "https", "s3")));


    private final S3DownloaderCoreConfiguration configuration;

    /**
     * Initializes an instance of this class which will be configured with the provided configuration.
     * This object will register itself on the provided facade.
     *
     * @param s3DownloaderCoreConfiguration The configuration for this instance
     */
    @Autowired
    public S3CoreDownloader(S3DownloaderCoreConfiguration s3DownloaderCoreConfiguration) {
        this.configuration = s3DownloaderCoreConfiguration;
    }

    @Override
    public Set<String> getProtocols() {
        return PROTOCOLS;
    }

    @Override
    public int getPriority(URI uri) {
        return Downloader.super.getPriority(uri);
    }

    @Override
    public Path download(DownloadRequest downloadRequest) throws IOException {
        LOG.info("Downloading: {} on {}", downloadRequest.getDownloadUri(), downloadRequest.getDownloadFolder());

        S3AccountProperties properties = configuration.getAccountProperties(downloadRequest.getDownloadUri());

        S3Location location = properties.getLocation(downloadRequest.getDownloadUri());

        String key = location.extractKey(downloadRequest.getDownloadUri());
        LOG.info("S3 Key: {}", key);

        if (key.endsWith("/")) {
            throw new UnsupportedOperationException("Download of multiple objects is not implemented: " + key);
        }

        AmazonS3 s3Client = initS3Client(properties, location);

        return downloadObject(s3Client, location.getBucket(), key, downloadRequest.getDownloadFolder());
    }

    private static AmazonS3 initS3Client(S3AccountProperties properties, S3Location location) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");
        clientConfiguration.setMaxErrorRetry(properties.getRetries());
        clientConfiguration.setConnectionTimeout(properties.getConnectionTimeout());
        clientConfiguration.setSocketTimeout(properties.getSocketTimeout());
        clientConfiguration.setProtocol("HTTP".equals(location.getBaseUrl().getScheme().toUpperCase()) ? Protocol.HTTP : Protocol.HTTPS);

        return AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(location.getEndpoint(), location.getRegion()))
                .withPathStyleAccessEnabled(location.isPathStyle())
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(properties.getAccessKey(), properties.getPrivateKey())))
                .build();
    }

    private Path downloadObject(AmazonS3 s3Client, String bucket, String key, Path targetDir) throws IOException {
        LOG.info("Downloading object {} from bucket {} into {}", key, bucket, targetDir);

        Path outputFilePath = targetDir.resolve(key);

        if (!Files.exists(outputFilePath.getParent())) {
            LOG.info("Creating folder {}", outputFilePath.getParent());
            Files.createDirectories(outputFilePath.getParent());
        }

        LOG.info("Download destination {} ", outputFilePath);
        try (S3Object object = s3Client.getObject(bucket, key)) {
            Files.copy(object.getObjectContent(), outputFilePath);
        }

        LOG.info("Successfully downloaded via S3: {} within dir {}", key, targetDir);

        return outputFilePath;
    }
}
