package com.cgi.eoss.platform.core.processing.worker;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.testutils.PersistenceTestUtils;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.repository.KubernetesWorkerJobRepository;
import com.cgi.eoss.platform.core.queues.QueuesCoreTestConfig;
import com.google.common.jimfs.Jimfs;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.nio.file.FileSystem;
import java.util.concurrent.TimeUnit;

@TestConfiguration
@Import(value = { QueuesCoreTestConfig.class })
@ComponentScan(basePackageClasses = { PersistenceTestUtils.class })
public class WorkerCoreTestConfig {

    @Bean
    public PersistenceTestUtils persistenceTestUtils(KubernetesWorkerJobRepository kubernetesWorkerJobRepository) {
        return new PersistenceTestUtils(kubernetesWorkerJobRepository);
    }

    @Bean
    public FileSystem mockFileSystem() {
        return Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());
    }

    @Bean("workflowApiClient")
    public ApiClient workflowApiClient(
        @Value("${platform.kubernetes.url:localhost}") String url,
        @Value("${platform.kubernetes.workflow.api.client.read.timeout.seconds:0}") Integer apiClientReadTimeoutSeconds) {
        return initApiClient(url, apiClientReadTimeoutSeconds);
    }

    private static ApiClient initApiClient(String url, int readTimeout) {
        ApiClient apiClient = Config.fromUrl(url);
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder()
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .build());
        return apiClient;
    }

}
