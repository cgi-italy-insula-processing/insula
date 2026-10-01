package com.cgi.eoss.platform.core.processing.outputuploader;

import com.cgi.eoss.platform.testutils.core.FilesUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = {OutputUploaderCoreTestConfig.class})
public abstract class StageOutServiceIT {

    private static final Path RESOURCES_PATH = Paths.get("src", "test", "resources").toAbsolutePath();

    @Autowired
    protected StageOutService stageOutService;

    public static class StageOutServiceWithDefaultThresholdIT extends StageOutServiceIT {

        @Test
        public void testStageOut_CreatesZipFileContainingItemReferredInCatalogAndItsAssets_WhenCatalogSizeIsBelowThreshold() throws Exception {
            Path stageOutFiles = RESOURCES_PATH.resolve("stage-out");

            Path catalogPath = stageOutFiles.resolve("catalog.json");

            Path tempDir = Files.createTempDirectory("stage-out");

            List<Path> zipPaths = stageOutService.archiveStacCatalog(catalogPath, tempDir);

            assertThat(zipPaths).containsExactlyInAnyOrder(tempDir.resolve("test-item.zip"));

            // Checks on the first generated archive
            {
                Path firstArchiveTempUnzipDir = Files.createTempDirectory("stage-out-unzip");

                FilesUtils.unzipFile(tempDir.resolve("test-item.zip"), firstArchiveTempUnzipDir);
                List<Path> firstArchiveUnzippedFiles = Files.walk(firstArchiveTempUnzipDir).filter(Files::isRegularFile).collect(Collectors.toList());

                assertThat(firstArchiveUnzippedFiles).hasSize(3);
                assertThat(firstArchiveUnzippedFiles).containsExactlyInAnyOrder(firstArchiveTempUnzipDir.resolve("test-file-1.txt"), firstArchiveTempUnzipDir.resolve("test-file-2.txt"), firstArchiveTempUnzipDir.resolve("item.json"));
                assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("test-file-1.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-1.txt")));
                assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("test-file-2.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-2.txt")));
                assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("item.json"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("item.json")));
            }
        }
    }

    @TestPropertySource(properties = {"platform.output.uploader.stage-out.catalogFilesizeThresholdBytes=280"})
    public static class StageOutServiceITWithLowerThresholdIT extends StageOutServiceIT {

        @Test
        public void testStageOut_ThrowsIllegalArgumentException_WhenCatalogFileSizeIsAboveThreshold() throws Exception {
            Path stageOutFiles = RESOURCES_PATH.resolve("stage-out");
            Path catalogPath = stageOutFiles.resolve("catalog.json");
            Path dest = Files.createTempDirectory("not-used-output");


            assertThatThrownBy(() -> stageOutService.archiveStacCatalog(catalogPath, dest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File " + catalogPath + " is too large");
        }
    }
}
