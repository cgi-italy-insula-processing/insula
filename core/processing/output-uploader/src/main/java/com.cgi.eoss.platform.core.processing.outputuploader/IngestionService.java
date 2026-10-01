package com.cgi.eoss.platform.core.processing.outputuploader;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.cgi.eoss.platform.core.processing.outputuploader.IngestionServiceProperties;
import com.cgi.eoss.platform.core.processing.outputuploader.StageOutService;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that ingests all output files from a Job located within a specified directory,
 * and uploads them to an S3 bucket.
 */
@Log4j2
@AllArgsConstructor
public class IngestionService {

    private final IngestionServiceProperties ingestionServiceProperties;

    private final AmazonS3 amazonS3;

    private final TransferManager transferManager;

    private final StageOutService stageOutService;

    private final ExecutorService executorService;

    /**
     * Uploads all output files within a specified directory to an S3 bucket.
     *
     * @param workingDir the root directory containing the output files to upload
     * @param jobId      job ID
     * @throws IOException if an I/O error during upload
     */
    public void uploadOutputs(Path workingDir, String jobId, Map<String, String> jobOutputs) throws IOException {

        LOG.info("Listing outputs from job {} in path: {}", jobId, workingDir);
        String bucketName = ingestionServiceProperties.getJobOutputsBucketName();
        ensureBucketExists(bucketName);
        List<Path> filesToUpload = jobOutputs.isEmpty()
                ? collectFilesFromDir(workingDir)
                : collectFilesFromStageOut(workingDir, jobOutputs);
        try {
            CompletableFuture.allOf(
                    filesToUpload.stream()
                            .map(file -> CompletableFuture.runAsync(() ->
                                    uploadFile(bucketName, file, workingDir, jobId), executorService))
                            .toArray(CompletableFuture[]::new)
            ).join();
            LOG.info("All uploads completed for job {}", jobId);
        } catch (CompletionException  e) {
            LOG.error("Failed to upload files for job {}", jobId, e);
            throw new IOException("Failed to upload files for job " + jobId, e);
        }
    }

    /**
     * Shutdown this service.
     */
    @PreDestroy
    public void cleanup() {
        transferManager.shutdownNow(false);
    }

    private static List<Path> collectFilesFromDir(Path workingDir) throws IOException {
        try (Stream<Path> files = Files.walk(workingDir, 3, FileVisitOption.FOLLOW_LINKS)) {
            return files
                    .filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private List<Path> collectFilesFromStageOut(Path workingDir, Map<String, String> jobOutputs) {
        return jobOutputs.entrySet().stream()
                .flatMap(entry -> stageOut(workingDir, entry.getValue(), entry.getKey()).stream())
                .collect(Collectors.toList());
    }

    private List<Path> stageOut(Path workingDir, String stageOutDir, String outputId) {
        Path catalogPath = Paths.get(stageOutDir).resolve("catalog.json");
        Path archivesDir = workingDir.resolve(outputId);
        try {
            if (!Files.exists(archivesDir)) {
                Files.createDirectories(archivesDir);
            }

            return stageOutService.archiveStacCatalog(catalogPath, archivesDir);
        } catch (IOException e) {
            LOG.error("Error while staging out outputs with id {}", outputId, e);
            throw new UncheckedIOException(e);
        }
    }

    private void uploadFile(String bucketName, Path filePath, Path outputDir, String jobId) {
        try {
            LOG.info("Uploading file {} from outputdir {}", filePath, outputDir);
            String key = Paths.get(jobId).resolve(outputDir.relativize(filePath)).toString();
            Upload upload = transferManager.upload(bucketName, key, filePath.toFile());
            upload.waitForCompletion();
            LOG.debug("Upload completed: {}", key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(new IOException("Upload interrupted: " + filePath, e));
        } catch (Exception e) {
            throw new CompletionException(new IOException("Error uploading: " + filePath, e));
        }
    }

    private void ensureBucketExists(String bucketName) {
        LOG.debug("Destination S3 bucket: {}", bucketName);
        if (!amazonS3.doesBucketExist(bucketName)) {
            amazonS3.createBucket(bucketName);
        }
    }

}
