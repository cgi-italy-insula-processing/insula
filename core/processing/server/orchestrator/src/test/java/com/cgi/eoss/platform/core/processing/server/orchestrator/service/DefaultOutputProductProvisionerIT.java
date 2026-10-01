package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.internal.OutputProductMetadata;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class DefaultOutputProductProvisionerIT {

    @Autowired
    private OutputProductProvisioner outputProductProvisioner;

    @Value("${platform.orchestrator.outputProducts.baseDir}")
    private Path baseDir;

    @Before
    public void setUp() {
        FilesUtils.deleteDirContentsIfExists(baseDir);
    }

    @After
    public void tearDown() {
        FilesUtils.deleteDirContentsIfExists(baseDir);
    }

    @Test
    public void testProvisionNewOutputProduct_provisionsPathUnderBaseDirAndCreatesParentDirectories() throws IOException {
        String jobId = UUID.randomUUID().toString();

        Path provisionedPath = outputProductProvisioner.provisionNewOutputProduct(
                OutputProductMetadata.builder().jobId(jobId).build(),
                "outputId1/content.tiff");

        assertThat(provisionedPath).isEqualTo(
                baseDir.toAbsolutePath().resolve(jobId).resolve("outputId1").resolve("content.tiff"));

        // thin provisioning: the parent directory is created, the file itself is not
        assertThat(Files.isDirectory(provisionedPath.getParent())).isTrue();
        assertThat(Files.exists(provisionedPath)).isFalse();
    }

}
