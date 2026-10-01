package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.core.processing.server.model.utils.JobConfigBuilder;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.core.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class JobOutputRepatriationCoreServiceIT {

    private static final String FILE_CONTENT = "content";

    private static final Path OUTPUT_PRODUCTS_SOURCE_FOLDER = Paths.get("target", "data", "outputProductsSource");

    private static final Path STAGE_OUT_OUTPUT_ARCHIVE = Paths.get("src", "test", "resources");

    @Autowired
    private JobOutputRepatriationCoreService jobOutputRepatriationCoreService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Value("${platform.orchestrator.outputProducts.baseDir}")
    private Path outputProductsDestinationFolder;

    private User platformUser;

    @Before
    public void init() throws IOException {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        platformUser = userDataService.save(ProcessingCoreEntities.createUser().name("platform").role(Role.GUEST).build());

        deleteTestDirsContents();
        createContentFiles();
    }

    @After
    public void shutdown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        deleteTestDirsContents();
    }

    @Test
    public void testRepatriateOutputFiles_WritesSingleFileToOutputStorage_WhenOutputIsNotStageOut() throws Exception {
        String jobExtId = UUID.randomUUID().toString();
        Job job = ProcessingCoreEntities.createJob(platformUser, createJobConfig(ImmutableMap.of("format", "GEOTIFF"), null))
                .extId(jobExtId).id(42L).build();

        List<Path> repatriatedPaths;
        try (InputStream inputStream = Files.newInputStream(OUTPUT_PRODUCTS_SOURCE_FOLDER.resolve("content.tiff"))) {
            repatriatedPaths = jobOutputRepatriationCoreService.repatriateOutputFiles(job, "outputId1", Paths.get("content.tiff"), inputStream);
        }

        assertThat(repatriatedPaths).hasSize(1);
        assertThat(repatriatedPaths.get(0).getFileName().toString()).isEqualTo("content.tiff");

        assertThat(FilesUtils.list(outputProductsDestinationFolder.resolve(jobExtId))).hasSize(1);
        Path repatriatedFile = outputProductsDestinationFolder.resolve(jobExtId).resolve("content.tiff");
        assertThat(StringUtils.readAsString(repatriatedFile)).isEqualTo(FILE_CONTENT);
    }

    @Test
    public void testRepatriateOutputFiles_UnzipsAndWritesAllFilesIncludingMetadata_WhenOutputIsStageOut() throws Exception {
        String jobExtId = UUID.randomUUID().toString();
        Job job = ProcessingCoreEntities.createJob(platformUser, createJobConfig(
                ImmutableMap.of("format", "OTHER", "type", "Stac"),
                PlatformServiceDescriptor.OutputBinding.builder().glob(".").build()))
                .extId(jobExtId).id(42L).build();

        String outputId = "outputId1";
        List<Path> repatriatedPaths;
        try (InputStream inputStream = Files.newInputStream(STAGE_OUT_OUTPUT_ARCHIVE.resolve("test-item.zip"))) {
            repatriatedPaths = jobOutputRepatriationCoreService.repatriateOutputFiles(job, outputId, Paths.get("test-item.zip"), inputStream);
        }

        assertThat(repatriatedPaths).hasSize(2);
        assertThat(repatriatedPaths).extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrder("content", "test-item.json");

        Path outputDir = outputProductsDestinationFolder.resolve(jobExtId).resolve(outputId);
        assertThat(FilesUtils.list(outputDir)).hasSize(2);
        assertThat(StringUtils.readAsString(outputDir.resolve("content"))).isEqualTo("justsomebytes");
        assertThat(StringUtils.readAsString(outputDir.resolve("test-item.json"))).contains("\"proj:code\": \"EPSG:3246\"");
    }

    @Test
    public void testRepatriateOutputFiles_ThrowsIoException_WhenInvalidZipIsPassed() throws Exception {
        String jobExtId = UUID.randomUUID().toString();
        Job job = ProcessingCoreEntities.createJob(platformUser, createJobConfig(
                ImmutableMap.of("format", "OTHER", "type", "STAC"),
                PlatformServiceDescriptor.OutputBinding.builder().glob(".").build()))
                .extId(jobExtId).id(42L).build();

        Path tempDir = Files.createTempDirectory("test");
        Path zipFile = tempDir.resolve("wrong.zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry("../invalid-entry.txt");
            zos.putNextEntry(entry);
            zos.write("Content for the wrong path".getBytes());
            zos.closeEntry();
        }

        try (InputStream inputStream = Files.newInputStream(zipFile)) {
            jobOutputRepatriationCoreService.repatriateOutputFiles(job, "outputId1", Paths.get("test-item.zip"), inputStream);
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage()).contains("Entry is outside of the target dir:");
        }
    }

    private void deleteTestDirsContents() {
        FilesUtils.deleteDirContentsIfExists(OUTPUT_PRODUCTS_SOURCE_FOLDER);
        FilesUtils.deleteDirContentsIfExists(outputProductsDestinationFolder);
    }

    private static void createContentFiles() throws IOException {
        Files.createDirectories(OUTPUT_PRODUCTS_SOURCE_FOLDER);
        Files.write(OUTPUT_PRODUCTS_SOURCE_FOLDER.resolve("content.tiff"), FILE_CONTENT.getBytes());
    }

    private JobConfig createJobConfig(Map<String, String> outputPlatformMetadata, PlatformServiceDescriptor.OutputBinding outputBinding) {
        PlatformService platformService = createPlatformService(createPlatformServiceDescriptor(outputPlatformMetadata, outputBinding));
        return jobConfigDataService.save(new JobConfigBuilder(platformUser, platformService)
                .withInputs(ImmutableMultimap.of(
                        platformService.getServiceDescriptor().getDataInputs().get(0).getId(), "inputId1_value1"))
                .build());
    }

    private PlatformService createPlatformService(PlatformServiceDescriptor platformServiceDescriptor) {
        return serviceDataService.save(ProcessingCoreEntities.createPlatformService(platformUser)
                .name("platformServiceName")
                .dockerTag("theDockerTag")
                .status(PlatformService.Status.AVAILABLE)
                .platformServiceDescriptor(platformServiceDescriptor).build());
    }

    private static PlatformServiceDescriptor createPlatformServiceDescriptor(Map<String, String> outputPlatformMetadata, PlatformServiceDescriptor.OutputBinding outputBinding) {
        PlatformServiceDescriptor.Parameter outputParameter = PlatformServiceDescriptor.Parameter.builder()
                .id("outputId1")
                .platformMetadata(outputPlatformMetadata)
                .outputBinding(outputBinding)
                .build();

        return PlatformServiceDescriptor.builder()
                .dataInputs(ImmutableList.of(PlatformServiceDescriptor.Parameter.builder().id("inputId1").build()))
                .dataOutputs(ImmutableList.of(outputParameter))
                .build();
    }

}
