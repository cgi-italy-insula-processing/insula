package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that turns requests for job output identifiers into filesystem paths
 *
 */
@Log4j2
@AllArgsConstructor
public class JobOutputLocator {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{1,127}");

    private final Path root;

    /**
     * Retrieves the folder holding the files of one output of one job.
     *
     * @param jobExtId Identifier of the job whose output folder should be retrieved
     * @param outputId Identifier of the job output whose folder should be retrieved
     *
     * @return The folder holding the files of the requested job output
     */
    public Path getOutputFolder(String jobExtId, String outputId) {
        assertValidIdentifier("jobExtId", jobExtId);
        assertValidIdentifier("outputId", outputId);

        Path jobDir = root.resolve(jobExtId);
        if (!Files.isDirectory(jobDir)) {
            throw new IllegalArgumentException("Job output folder not found for job: " + jobExtId);
        }
        Path outputFolder = jobDir.resolve(outputId);
        if (!Files.isDirectory(outputFolder)) {
            throw new IllegalArgumentException("Job output folder not found for output: " + outputFolder + " of job " + jobExtId);
        }
        return outputFolder;
    }

    /**
     * Retrieves the path to the file within the output folder of the provided job.
     *
     * @param jobExtId Identifier of the job whose output file should be retrieved
     * @param outputId Identifier of the job output whose file should be retrieved
     * @param filepath Path to the file to retrieve
     *
     * @return The path to the job output file
     */
    public Path getOutputFile(String jobExtId, String outputId, String filepath) {
        Path outputFolder = getOutputFolder(jobExtId, outputId);

        Path file = outputFolder.resolve(relativePath(filepath)).normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No such file: " + file);
        }
        return file;
    }

    /**
     * Retrieves the paths to the files within the output folder of the provided job, nested folders included.
     *
     * @param jobExtId Identifier of the job whose output files should be retrieved
     * @param outputId Identifier of the job output whose files should be retrieved
     *
     * @return The paths to the job output files, sorted by path
     */
    public List<Path> getOutputFiles(String jobExtId, String outputId) {
        Path outputFolder = getOutputFolder(jobExtId, outputId);

        try (Stream<Path> entries = Files.walk(outputFolder, 16)) {
            return entries.filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list output folder " + outputFolder, e);
        }
    }

    private static String relativePath(String filepath) {
        String path = filepath.startsWith("/") ? filepath.substring(1) : filepath;
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Empty file path");
        }
        for (String segment : path.split("/", -1)) {
            if (!isValidSegment(segment)) {
                throw new IllegalArgumentException("Invalid file path '" + filepath + "'");
            }
        }
        return path;
    }

    private static boolean isValidSegment(String segment) {
        return !segment.isEmpty() && !segment.equals(".") && !segment.equals("..")
                && segment.indexOf('\\') < 0 && segment.indexOf('\0') < 0;
    }

    private static void assertValidIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid identifier " + name + ": '" + value + "'");
        }
    }

}
