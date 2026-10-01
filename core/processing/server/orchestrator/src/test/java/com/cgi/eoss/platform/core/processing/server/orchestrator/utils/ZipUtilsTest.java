package com.cgi.eoss.platform.core.processing.server.orchestrator.utils;

import com.cgi.eoss.platform.testutils.core.StringUtils;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class ZipUtilsTest {

    private static final Path TEST_DATA_DIR = Paths.get("src", "test", "resources");

    @Test
    public void testUnzipInFolder_UnzipsFilesInTheProvidedFolder() throws Exception {
        Path tempUnzipDir = Files.createTempDirectory("unzip-");

        Path destDir;
        try (InputStream inputStream = Files.newInputStream(TEST_DATA_DIR.resolve("archive-with-file.zip"))) {
            destDir = ZipUtils.unzipInFolder(inputStream, tempUnzipDir);
        }

        assertThat(destDir).isEqualTo(tempUnzipDir);
        assertThat(Files.walk(tempUnzipDir)).containsExactlyInAnyOrder(
                tempUnzipDir,
                tempUnzipDir.resolve("zipcontent"));

        assertThat(StringUtils.readAsString(tempUnzipDir.resolve("zipcontent"))).isEqualToIgnoringNewLines("zippedfilecontent");
    }

    @Test
    public void testUnzipInFolder_UnzipsFilesInTheProvidedFolder_WhenZipFileContainsDirectory() throws Exception {
        Path tempUnzipDir = Files.createTempDirectory("unzip-");

        Path destDir;
        try (InputStream inputStream = Files.newInputStream(TEST_DATA_DIR.resolve("archive-with-nested-dir.zip"))) {
            destDir = ZipUtils.unzipInFolder(inputStream, tempUnzipDir);
        }

        assertThat(destDir).isEqualTo(tempUnzipDir);
        assertThat(Files.walk(tempUnzipDir)).containsExactlyInAnyOrder(
                tempUnzipDir,
                tempUnzipDir.resolve("emptyzipdir"),
                tempUnzipDir.resolve("zipcontent.txt"),
                tempUnzipDir.resolve("zipdir"),
                tempUnzipDir.resolve("zipdir").resolve("zipdircontent.txt")
                );

        assertThat(StringUtils.readAsString(tempUnzipDir.resolve("zipcontent.txt"))).isEqualToIgnoringNewLines("zipcontent");
        assertThat(StringUtils.readAsString(tempUnzipDir.resolve("zipdir").resolve("zipdircontent.txt"))).isEqualToIgnoringNewLines("zipdircontent");
    }

    @Test
    public void testUnzipInFolder_CreatesFolderAndUnzipsFilesInIt_WhenDestinationDirectoryDoesNotExists() throws Exception {
        Path tempUnzipDir = Files.createTempDirectory("unzip-");


        Path destDir = tempUnzipDir.resolve("to-be-created");
        assertThat(destDir).doesNotExist();
        try (InputStream inputStream = Files.newInputStream(TEST_DATA_DIR.resolve("archive-with-nested-dir.zip"))) {
            destDir = ZipUtils.unzipInFolder(inputStream, destDir);
        }

        assertThat(destDir).isDirectory();
        assertThat(Files.walk(destDir)).containsExactlyInAnyOrder(
                destDir,
                destDir.resolve("emptyzipdir"),
                destDir.resolve("zipcontent.txt"),
                destDir.resolve("zipdir"),
                destDir.resolve("zipdir").resolve("zipdircontent.txt")
        );

        assertThat(StringUtils.readAsString(destDir.resolve("zipcontent.txt"))).isEqualToIgnoringNewLines("zipcontent");
        assertThat(StringUtils.readAsString(destDir.resolve("zipdir").resolve("zipdircontent.txt"))).isEqualToIgnoringNewLines("zipdircontent");
    }


    @Test
    public void testUnzipInFolder_UnzipsFilesInTheProvidedFolder_WhenZipEntryContainsDirectoryAndDestFolderContainsADirectoryWithSameName() throws Exception {
        Path tempUnzipDir = Files.createTempDirectory("unzip-");
        tempUnzipDir.resolve("zipdir").toFile().mkdir();

        Path destDir;
        try (InputStream inputStream = Files.newInputStream(TEST_DATA_DIR.resolve("archive-with-nested-dir.zip"))) {
            destDir = ZipUtils.unzipInFolder(inputStream, tempUnzipDir);
        }

        assertThat(destDir).isEqualTo(tempUnzipDir);
        assertThat(Files.walk(tempUnzipDir)).containsExactlyInAnyOrder(
                tempUnzipDir,
                tempUnzipDir.resolve("emptyzipdir"),
                tempUnzipDir.resolve("zipcontent.txt"),
                tempUnzipDir.resolve("zipdir"),
                tempUnzipDir.resolve("zipdir").resolve("zipdircontent.txt")
        );

        assertThat(StringUtils.readAsString(tempUnzipDir.resolve("zipcontent.txt"))).isEqualToIgnoringNewLines("zipcontent");
        assertThat(StringUtils.readAsString(tempUnzipDir.resolve("zipdir").resolve("zipdircontent.txt"))).isEqualToIgnoringNewLines("zipdircontent");
    }

    @Test
    public void testUnzipInFolder_ThrowsIoException_WhenDestinationFolderContainsARegularFileWithSameNameAsZipEntryContainingADirectory() throws Exception {

        Path tempUnzipDir = Files.createTempDirectory("unzip-");
        tempUnzipDir.resolve("zipdir").toFile().createNewFile();

        try (InputStream inputStream = Files.newInputStream(TEST_DATA_DIR.resolve("archive-with-nested-dir.zip"))) {
            ZipUtils.unzipInFolder(inputStream, tempUnzipDir);
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("Failed to create directory " + Paths.get(tempUnzipDir.toString(), "zipdir"));
        }
    }

    @Test
    public void testUnzipInFolder_ThrowsIoException_WhenZipFileContainsAnInvalidEntry() throws IOException {
        Path tempDir = Files.createTempDirectory("unzip-");

        Path zipFile = tempDir.resolve("wrong.zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry("../invalid-entry.txt");
            zos.putNextEntry(entry);
            zos.write("Content for the wrong path".getBytes());
            zos.closeEntry();
        }

        try (InputStream zipInputStream = Files.newInputStream(zipFile.toFile().toPath())) {
            IOException exception = assertThrows(IOException.class,
                    () -> ZipUtils.unzipInFolder(zipInputStream, tempDir));
            assertThat(exception.getMessage()).contains("Entry is outside of the target dir");
        }
    }
}