package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import lombok.extern.log4j.Log4j2;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@Log4j2
public class JobOutputLocatorTest {

    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    private final Path root = fileSystem.getPath("/data/outputProducts").toAbsolutePath();

    private final JobOutputLocator jobOutputLocator = new JobOutputLocator(root);

    @Before
    public void init() throws IOException {
        assertThat(root).doesNotExist();
        Files.createDirectories(root);
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenJobExtIdIsNull() {

        try {
            jobOutputLocator.getOutputFolder(null, "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier jobExtId: 'null'");
        }
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenJobExtIdIsNotValid() {

        try {
            jobOutputLocator.getOutputFolder(".", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier jobExtId: '.'");
        }
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenOutputIdIsNull() {

        try {
            jobOutputLocator.getOutputFolder("jobExtId", null);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier outputId: 'null'");
        }
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenOutputIdIsNotValid() {

        try {
            jobOutputLocator.getOutputFolder("jobExtId", "..");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier outputId: '..'");
        }
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenJobFolderDoesNotExists() {

        try {
            jobOutputLocator.getOutputFolder("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for job: jobExtId");
        }
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenJobFolderIsFile() throws IOException {

        Files.createFile(root.resolve("jobExtId"));

        try {
            jobOutputLocator.getOutputFolder("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for job: jobExtId");
        }
    }

    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenJobOutputFolderDoesNotExists() throws IOException {

        Files.createDirectory(root.resolve("jobExtId"));

        try {
            jobOutputLocator.getOutputFolder("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for output: /data/outputProducts/jobExtId/outputId of job jobExtId");
        }
    }


    @Test
    public void testGetOutputFolder_ThrowsIllegalArgumentException_WhenJobOutputFolderIsFile() throws IOException {

        Path jobFolder = Files.createDirectory(root.resolve("jobExtId"));
        Files.createFile(jobFolder.resolve("outputId"));

        try {
            jobOutputLocator.getOutputFolder("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for output: /data/outputProducts/jobExtId/outputId of job jobExtId");
        }
    }

    @Test
    public void testGetOutputFolder_ReturnsPathToTheJobOutputFolder() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        assertThat(jobOutputLocator.getOutputFolder("jobExtId", "outputId"))
                .isEqualTo(fileSystem.getPath("/data/outputProducts/jobExtId/outputId"));
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenJobExtIdIsNull() {

        try {
            jobOutputLocator.getOutputFiles(null, "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier jobExtId: 'null'");
        }
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenJobExtIdIsNotValid() {

        try {
            jobOutputLocator.getOutputFiles(".", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier jobExtId: '.'");
        }
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenOutputIdIsNull() {

        try {
            jobOutputLocator.getOutputFiles("jobExtId", null);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier outputId: 'null'");
        }
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenOutputIdIsNotValid() {

        try {
            jobOutputLocator.getOutputFiles("jobExtId", "..");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier outputId: '..'");
        }
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenJobFolderDoesNotExists() {

        try {
            jobOutputLocator.getOutputFiles("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for job: jobExtId");
        }
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenJobFolderIsFile() throws IOException {

        Files.createFile(root.resolve("jobExtId"));

        try {
            jobOutputLocator.getOutputFiles("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for job: jobExtId");
        }
    }

    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenJobOutputFolderDoesNotExists() throws IOException {

        Files.createDirectory(root.resolve("jobExtId"));

        try {
            jobOutputLocator.getOutputFiles("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for output: /data/outputProducts/jobExtId/outputId of job jobExtId");
        }
    }


    @Test
    public void testGetOutputFiles_ThrowsIllegalArgumentException_WhenJobOutputFolderIsFile() throws IOException {

        Path jobFolder = Files.createDirectory(root.resolve("jobExtId"));
        Files.createFile(jobFolder.resolve("outputId"));

        try {
            jobOutputLocator.getOutputFiles("jobExtId", "outputId");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for output: /data/outputProducts/jobExtId/outputId of job jobExtId");
        }
    }

    @Test
    public void testGetOutputFiles_ReturnsPathsToTheJobOutputFilesSortedByPathSkippingDirectories() throws IOException {

        Path jobOutputFolder = Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        Path jobOutputFileC = Files.createFile(jobOutputFolder.resolve("fileC"));
        Path jobOutputFileB = Files.createFile(jobOutputFolder.resolve("fileB"));
        Path jobOutputNestedFolder = Files.createDirectories(jobOutputFolder.resolve("nested"));
        Path jobOutputFileA = Files.createFile(jobOutputNestedFolder.resolve("fileA"));

        assertThat(jobOutputLocator.getOutputFiles("jobExtId", "outputId"))
                .isEqualTo(ImmutableList.of(jobOutputFileB, jobOutputFileC, jobOutputFileA));
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobExtIdIsNull() {

        try {
            jobOutputLocator.getOutputFile(null, "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier jobExtId: 'null'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobExtIdIsNotValid() {

        try {
            jobOutputLocator.getOutputFile(".", "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier jobExtId: '.'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenOutputIdIsNull() {

        try {
            jobOutputLocator.getOutputFile("jobExtId", null, "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier outputId: 'null'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenOutputIdIsNotValid() {

        try {
            jobOutputLocator.getOutputFile("jobExtId", "..", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid identifier outputId: '..'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobFolderDoesNotExists() {

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for job: jobExtId");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobFolderIsFile() throws IOException {

        Files.createFile(root.resolve("jobExtId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for job: jobExtId");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFolderDoesNotExists() throws IOException {

        Files.createDirectory(root.resolve("jobExtId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for output: /data/outputProducts/jobExtId/outputId of job jobExtId");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFolderIsFile() throws IOException {

        Path jobFolder = Files.createDirectory(root.resolve("jobExtId"));
        Files.createFile(jobFolder.resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Job output folder not found for output: /data/outputProducts/jobExtId/outputId of job jobExtId");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameIsFilesystemRoot() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "/");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Empty file path");
        }
    }


    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameContainsEmptyPathSegment() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "file//Name");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid file path 'file//Name'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameContainsDotPathSegment() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "file/./Name");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid file path 'file/./Name'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameContainsBackwardSlashPathSegment() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "file/\\/Name");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid file path 'file/\\/Name'");
        }
    }


    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameContainsZeroCharPathSegment() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "file/\0/Name");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid file path 'file/\0/Name'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameIsInvalidAbsolutePath() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "/../../../fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid file path '/../../../fileName'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameIsInvalidRelativePath() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "../../../fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Invalid file path '../../../fileName'");
        }
    }

    @Test
    public void testGetOutputFile_ThrowsIllegalArgumentException_WhenJobOutputFileNameIsFolder() throws IOException {

        Files.createDirectories(root.resolve("jobExtId").resolve("outputId").resolve("fileName"));

        try {
            jobOutputLocator.getOutputFile("jobExtId", "outputId", "fileName");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("No such file: /data/outputProducts/jobExtId/outputId/fileName");
        }
    }

    @Test
    public void testGetOutputFile_ReturnsPathToTheJobOutputFileNestedFoldersIncluded() throws IOException {

        Path jobOutputNestedFolder = Files.createDirectories(root.resolve("jobExtId").resolve("outputId").resolve("nested"));
        Files.createFile(jobOutputNestedFolder.resolve("fileName"));

        assertThat(jobOutputLocator.getOutputFile("jobExtId", "outputId", "nested/fileName"))
                .isEqualTo(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/nested/fileName"));
    }

}
