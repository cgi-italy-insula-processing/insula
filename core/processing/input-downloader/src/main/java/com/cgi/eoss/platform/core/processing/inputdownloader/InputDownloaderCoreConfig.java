package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.IoCoreConfig;
import com.cgi.eoss.platform.core.processing.io.download.Downloader;
import com.cgi.eoss.platform.core.processing.io.download.DownloaderFacade;
import com.cgi.eoss.platform.core.processing.io.download.SimpleDownloaderFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Strings;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Class to configure the inputDownloaderCore application and its related beans.
 * */
@Configuration
@Import({IoCoreConfig.class})
public class InputDownloaderCoreConfig {

    /**
     * Creates and registers a {@link EnvironmentService}  bean within the Spring application context.
     * @param stageInService The available stageInService bean
     * @param downloaderService The available downloaderService bean
     * @param k8sJobParams The available bean representing configured k8sJobParams
     * @param mapper The available configured mapper.
     * @return The configured {@code EnvironmentService} bean.
     * */
    @Bean
    public EnvironmentService environmentService(
            StageInService stageInService,
            DownloaderService downloaderService,
            K8sJobParams k8sJobParams,
            ObjectMapper mapper) {

        return new EnvironmentService(stageInService, downloaderService, k8sJobParams, mapper);
    }

    /**
     * Creates the {@code basePath} used by {@link InputDownloaderRunner} as base path for operations on file system.
     * @param basePath The path name used.
     * @return The {@code Path} representing the basePath
     * */
    @Bean
    public Path basePath(@Value("${base_path}") String basePath) {
        return Paths.get(basePath);
    }

    /**
     * Creates the {@code stacDocumentTempFolder} used by {@link StageInService} to download stac items.
     * @param stacDocumentTempFolderStr The path name used to create the folder.
     * @return The {@code Path} representing the stacDocumentTempFolder
     * */
    @Bean
    public Path stacDocumentTempFolder(@Value("${platform.inputdownloader.stacDocumentTempFolder:${base_path}/tempDir}") String stacDocumentTempFolderStr) {
        return Paths.get(stacDocumentTempFolderStr);
    }

    /**
     * Creates a {@link StageInService} bean that uses the specified DownloaderFacade.
     * @param doNotUnzipDownloaderFacade The DownloaderFacade to use for downloading files.
     * @param stacDocumentTempFolder The temporary folder to use for STAC documents.
     * @return The configured {@code StageInService} bean.
     */
    @Bean
    public StageInService stageInService(DownloaderFacade doNotUnzipDownloaderFacade, Path stacDocumentTempFolder) {
        return new StageInService(doNotUnzipDownloaderFacade, new ObjectMapper(), stacDocumentTempFolder);
    }

    /**
     *<p>
     * Creates and registers a {@link DownloaderService} bean within the Spring application context.
     *</p>
     * @param downloaderFacade        downloaderFacade available bean,
     * @param createSubdirectories    if false, forces the downloader to save every input in the inputId
     *                                directory, without creating subdirectories
     * @return a configured instance of {@code DownloaderService}
     */
    @Bean
    public DownloaderService downloaderService(
            DownloaderFacade downloaderFacade,
            @Value("${platform.inputdownloader.createSubdirectories:true}") boolean createSubdirectories) {
        return new DownloaderService(downloaderFacade, createSubdirectories);
    }

    /**
     * Creates an HTTP client, using the {@code http_proxy} environment variable if set.
     *
     * @return the configured HTTP client.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        String httpProxy = System.getenv("http_proxy");
        if (!Strings.isNullOrEmpty(httpProxy)) {
            URI proxyUri = URI.create(httpProxy);
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
        }

        return builder.build();
    }

    /**
     * Configures and returns the {@link K8sJobParams}.
     *
     * @param jsonParameter the parameters as a JSON string.
     * @param objectMapper the object mapper for parsing JSON.
     * @return a configured {@code K8sJobParams}.
     */
    @Bean
    K8sJobParams k8sJobParams(
            @Value("${inputs}") String jsonParameter, ObjectMapper objectMapper) {

        return new K8sJobParams(jsonParameter, objectMapper);
    }

    /**
     * Creates an objectMapper with {@link GuavaModule} registered.
     *
     * @return the objectMapper bean.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new GuavaModule());
    }

    @Configuration
    @ConditionalOnProperty(
            name = "platform.inputdownloader.app",
            havingValue = "processing-core",
            matchIfMissing = false
    )
    public static class DefaultConfig {

        /**
         * Creates and registers a {@link DefaultInputDownloaderRunner} bean.
         *
         * @param environmentService service providing environment-level metadata and utilities
         * @param basePath           the root {@link Path} used as the base directory for input downloads
         * @param jobId              the unique identifier of the current job
         * @param jobOwner           the owner of the current job
         * @return a fully configured {@link DefaultInputDownloaderRunner} instance
         */
        @Bean
        public DefaultInputDownloaderRunner inputDownloaderRunner(
                EnvironmentService environmentService,
                @Qualifier("basePath") Path basePath,
                @Value("${job_id}") String jobId,
                @Value("${job_owner}") String jobOwner) {
            return new DefaultInputDownloaderRunner(environmentService, basePath, jobId, jobOwner);
        }

        /**
         * Creates the {@link SimpleDownloaderFacade}.
         *
         * @param downloaders the available downloaders.
         * @return the DownloaderFacade bean.
         */
        @Bean
        public SimpleDownloaderFacade downloaderFacade(Set<Downloader> downloaders) {
            return new SimpleDownloaderFacade(downloaders);
        }
    }
}
