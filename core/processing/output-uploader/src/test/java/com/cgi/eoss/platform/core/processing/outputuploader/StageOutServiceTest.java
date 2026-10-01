package com.cgi.eoss.platform.core.processing.outputuploader;

import com.cgi.eoss.platform.testutils.core.FilesUtils;
import org.junit.Test;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StageOutServiceTest {

    private static final Path RESOURCES_PATH = Paths.get("src", "test", "resources").toAbsolutePath();

    private final StageOutService stageOutService = new StageOutService(10000);

    @Test
    public void testStageOut_CreatesZipFileContainingItemReferredInCatalogAndItsAssetsAndIgnoresRootLink_WhenHrefsAreRelativePaths() throws Exception {
        Path stageOutFiles = RESOURCES_PATH.resolve("stage-out");

        Path catalogPath = stageOutFiles.resolve("catalog-multi.json");

        Path tempDir = Files.createTempDirectory("stage-out");

        List<Path> zipPaths = stageOutService.archiveStacCatalog(catalogPath, tempDir);

        assertThat(zipPaths).containsExactlyInAnyOrder(tempDir.resolve("test-item.zip"), tempDir.resolve("test-item-2.zip"));

        // Checks on the first generated archive
        {
            Path firstArchiveTempUnzipDir = Files.createTempDirectory("stage-out-unzip-first");

            FilesUtils.unzipFile(tempDir.resolve("test-item.zip"), firstArchiveTempUnzipDir);
            List<Path> firstArchiveUnzippedFiles = Files.walk(firstArchiveTempUnzipDir).filter(Files::isRegularFile).collect(Collectors.toList());

            assertThat(firstArchiveUnzippedFiles).hasSize(3);
            assertThat(firstArchiveUnzippedFiles).containsExactlyInAnyOrder(firstArchiveTempUnzipDir.resolve("test-file-1.txt"), firstArchiveTempUnzipDir.resolve("test-file-2.txt"), firstArchiveTempUnzipDir.resolve("item.json"));
            assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("test-file-1.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-1.txt")));
            assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("test-file-2.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-2.txt")));
            assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("item.json"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("item.json")));
        }

        // Checks on the second archive
        {
            Path secondArchiveTempUnzipDir = Files.createTempDirectory("stage-out-unzip-second");

            FilesUtils.unzipFile(tempDir.resolve("test-item-2.zip"), secondArchiveTempUnzipDir);
            List<Path> secondArchiveUnzippedFiles = Files.walk(secondArchiveTempUnzipDir).filter(Files::isRegularFile).collect(Collectors.toList());

            assertThat(secondArchiveUnzippedFiles).hasSize(2);
            assertThat(secondArchiveUnzippedFiles).containsExactlyInAnyOrder(secondArchiveTempUnzipDir.resolve("test-file-1.txt"), secondArchiveTempUnzipDir.resolve("item2.json"));
            assertThat(Files.readAllBytes(secondArchiveTempUnzipDir.resolve("test-file-1.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-1.txt")));
            assertThat(Files.readAllBytes(secondArchiveTempUnzipDir.resolve("item2.json"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("item2.json")));
        }
    }

    @Test
    public void testStageOut_CreatesZipFileContainingItemReferredInCatalogAndItsAssets_WhenHrefsAreAbsolutePaths() throws Exception {
        Path stageOutFiles = RESOURCES_PATH.resolve("stage-out");

        Path tempResourcesDir = Files.createTempDirectory("stage-out-input");

        Path tempItem = tempResourcesDir.resolve("item.json");
        String item = writeItem(stageOutFiles.resolve("test-file-1.txt"), stageOutFiles.resolve("test-file-2.txt"));
        Files.write(tempItem, item.getBytes(StandardCharsets.UTF_8));

        Path tempCatalog = tempResourcesDir.resolve("catalog.json");
        Files.write(tempCatalog, writeCatalog(tempItem).getBytes(StandardCharsets.UTF_8));

        Path destinationDir = Files.createTempDirectory("stage-out-");

        List<Path> zipPaths = stageOutService.archiveStacCatalog(tempCatalog, destinationDir);

        assertThat(zipPaths).containsExactlyInAnyOrder(destinationDir.resolve("test-item.zip"));

        {
            Path firstArchiveTempUnzipDir = Files.createTempDirectory("stage-out-unzip-first");

            FilesUtils.unzipFile(destinationDir.resolve("test-item.zip"), firstArchiveTempUnzipDir);
            List<Path> firstArchiveUnzippedFiles = Files.walk(firstArchiveTempUnzipDir).filter(Files::isRegularFile).collect(Collectors.toList());

            assertThat(firstArchiveUnzippedFiles).hasSize(3);
            assertThat(firstArchiveUnzippedFiles).containsExactlyInAnyOrder(firstArchiveTempUnzipDir.resolve("test-file-1.txt"), firstArchiveTempUnzipDir.resolve("test-file-2.txt"), firstArchiveTempUnzipDir.resolve("item.json"));
            assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("test-file-1.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-1.txt")));
            assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("test-file-2.txt"))).isEqualTo(Files.readAllBytes(stageOutFiles.resolve("test-file-2.txt")));
            assertThat(Files.readAllBytes(firstArchiveTempUnzipDir.resolve("item.json"))).isEqualTo(item.getBytes());
        }
    }

    @Test
    public void testStageOut_ThrowsIllegalArgumentException_WhenCatalogFileSizeIsAboveThreshold() throws Exception {
        Path stageOutFiles = RESOURCES_PATH.resolve("stage-out");
        Path catalogPath = stageOutFiles.resolve("catalog-multi.json");
        Path dest = Files.createTempDirectory("not-used-output");

        FileChannel channel = FileChannel.open(catalogPath);
        long catalogSize = channel.size();
        channel.close();

        StageOutService stageOutServiceWithCustomThreshold = new StageOutService(catalogSize - 1);
        assertThatThrownBy(() -> stageOutServiceWithCustomThreshold.archiveStacCatalog(catalogPath, dest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File " + catalogPath + " is too large");
    }



    private String writeCatalog(Path itemPath) {
        return "{\n" +
                "  \"id\": \"stage-out-catalog\",\n" +
                "  \"type\": \"catalog\",\n" +
                "  \"stac_version\": \"1.0.0\",\n" +
                "  \"description\": \"Root catalog for stage out test\",\n" +
                "  \"links\": [\n" +
                "    {\n" +
                "      \"rel\": \"item\",\n" +
                "      \"type\": \"application/geo+json\",\n" +
                "      \"title\": \"test-item\",\n" +
                "      \"href\": \"" + itemPath.toAbsolutePath() +"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"rel\": \"self\",\n" +
                "      \"type\": \"application/geo+json\",\n" +
                "      \"title\": \"test-item\",\n" +
                "      \"href\": \" invalid path cause it should not be read during stage out \"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";
    }

    private String writeItem(Path filePath1, Path filePath2) {
        return "{\n" +
                "  \"id\": \"test-item\",\n" +
                "  \"assets\": {\n" +
                "    \"test-1\": {\n" +
                "      \"href\": \"" + filePath1.toAbsolutePath() + "\"\n" +
                "    },\n" +
                "    \"test-2\": {\n" +
                "      \"href\": \"" + filePath2.toAbsolutePath() + "\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }
}