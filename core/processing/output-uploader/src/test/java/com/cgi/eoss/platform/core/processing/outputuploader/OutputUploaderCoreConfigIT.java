package com.cgi.eoss.platform.core.processing.outputuploader;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = {OutputUploaderCoreTestConfig.class})
public class OutputUploaderCoreConfigIT {

    @Autowired
    protected ApplicationContext applicationContext;

    @Test
    public void testOutputUploaderCoreConfig_CreatesStageOutServiceBean() {
        String[] stageOutService = applicationContext.getBeanNamesForType(StageOutService.class);
        assertThat(stageOutService).hasSize(1);
        assertThat(stageOutService[0]).isEqualTo("stageOutService");
    }

    @Test
    public void testOutputUploaderCoreConfig_CreatesDefaultIngestionServiceProperties() {
        String[] defaultIngestionServiceProperties = applicationContext.getBeanNamesForType(DefaultIngestionServiceProperties.class);
        assertThat(defaultIngestionServiceProperties).hasSize(1);
        assertThat(defaultIngestionServiceProperties[0]).isEqualTo(DefaultIngestionServiceProperties.class.getName());
    }

    @Test
    public void testOutputUploaderCoreConfig_CreatesAmazonS3Bean() {
        String[] amazonS3 =  applicationContext.getBeanNamesForType(AmazonS3.class);
        assertThat(amazonS3).hasSize(1);
        assertThat(amazonS3[0]).isEqualTo("amazonS3");
    }

    @Test
    public void testOutputUploaderCoreConfig_CreatesTransferManagerBean() {
        String[] transferManager =  applicationContext.getBeanNamesForType(TransferManager.class);
        assertThat(transferManager).hasSize(1);
        assertThat(transferManager[0]).isEqualTo("transferManager");
    }

    @Test
    public void testOutputUploaderCoreConfig_CreatesExecutorServiceBean() {
        String[] executorService =  applicationContext.getBeanNamesForType(ExecutorService.class);
        assertThat(executorService).hasSize(1);
        assertThat(executorService[0]).isEqualTo("executorService");
    }

    @Test
    public void testOutputUploaderCoreConfig_CreatesIngestionServiceBean() {
        String[] ingestionService =  applicationContext.getBeanNamesForType(IngestionService.class);
        assertThat(ingestionService).hasSize(1);
        assertThat(ingestionService[0]).isEqualTo("ingestionService");
    }

    @Test
    public void testOutputUploaderCoreConfig_CreatesDefaultOutputUploaderRunnerBean() {
        String[] defaultOutputUploaderRunner =  applicationContext.getBeanNamesForType(DefaultOutputUploaderRunner.class);
        assertThat(defaultOutputUploaderRunner).hasSize(1);
        assertThat(defaultOutputUploaderRunner[0]).isEqualTo("outputUploaderRunner");
    }

}
