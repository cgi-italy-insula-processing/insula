package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.download.SimpleDownloaderFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = InputDownloaderCoreConfig.class)
@TestPropertySource("classpath:test-input-downloader-core.properties")
public class InputDownloaderCoreConfigIT {

    @Autowired
    protected ApplicationContext applicationContext;

    @Test
    public void testInputDownloaderCoreConfig_CreatesDefaultInputDownloaderRunner() {
        String[] defaultInputDownloaderRunnerBean = applicationContext.getBeanNamesForType(DefaultInputDownloaderRunner.class);
        assertThat(defaultInputDownloaderRunnerBean).hasSize(1);
        assertThat(defaultInputDownloaderRunnerBean[0]).isEqualTo("inputDownloaderRunner");
    }

    @Test
    public void testInputDownloaderConfig_CreatesBasePathAndStacDocumentBeans() {
        String[] pathBeans = applicationContext.getBeanNamesForType(Path.class);
        assertThat(pathBeans).containsExactlyInAnyOrder(
                "basePath", "stacDocumentTempFolder"
        );
    }

    @Test
    public void testInputDownloaderCoreConfig_CreatesEnvironmentServiceBean() {
        String[] environmentServiceBean = applicationContext.getBeanNamesForType(EnvironmentService.class);
        assertThat(environmentServiceBean).hasSize(1);
        assertThat(environmentServiceBean[0]).isEqualTo("environmentService");
    }

    @Test
    public void testInputDownloaderCoreConfig_CreatesStageInServiceBean() {
        String[] stageInServiceBean = applicationContext.getBeanNamesForType(StageInService.class);
        assertThat(stageInServiceBean).hasSize(1);
        assertThat(stageInServiceBean[0]).isEqualTo("stageInService");
    }

    @Test
    public void testInputDownloaderCoreConfig_CreatesObjectMapperBean() {
        String[] objectMapperBean = applicationContext.getBeanNamesForType(ObjectMapper.class);
        assertThat(objectMapperBean).hasSize(1);
        assertThat(objectMapperBean[0]).isEqualTo("objectMapper");
    }

    @Test
    public void testInputDownloaderCoreConfig_CreatesK8sJobParamsBean() {
        String[] k8sJobParamsBean = applicationContext.getBeanNamesForType(K8sJobParams.class);
        assertThat(k8sJobParamsBean).hasSize(1);
        assertThat(k8sJobParamsBean[0]).isEqualTo("k8sJobParams");
    }

    @Test
    public void testInputDownloaderCoreConfig_CreatesOkHttpClientBean() {
        String[] okHttpClientBean = applicationContext.getBeanNamesForType(OkHttpClient.class);
        assertThat(okHttpClientBean).hasSize(1);
        assertThat(okHttpClientBean[0]).isEqualTo("okHttpClient");
    }

    @Test
    public void testInputDownloaderConfig_CreatesDownloaderServiceBean() {
        String[] downloaderServiceBean = applicationContext.getBeanNamesForType(DownloaderService.class);
        assertThat(downloaderServiceBean).hasSize(1);
        assertThat(downloaderServiceBean[0]).isEqualTo("downloaderService");
    }

    @Test
    public void testInputDownloaderConfig_CreatesSimpleDownloaderFacadeBean() {
        String[] downloaderFacadeBean = applicationContext.getBeanNamesForType(SimpleDownloaderFacade.class);
        assertThat(downloaderFacadeBean).hasSize(1);
        assertThat(downloaderFacadeBean[0]).isEqualTo("downloaderFacade");
    }


}
