package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cgi.eoss.platform.core.processing.server.orchestrator.utils.ZipUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor.Parameter;
import com.cgi.eoss.platform.core.processing.server.model.internal.OutputProductMetadata;

/**
 * <p>Repatriates a job output: pulls the bytes from the given input stream into the platform output product storage and
 * returns the path(s) where the files have been written.</p>
 */
@AllArgsConstructor
@Slf4j
public class JobOutputRepatriationCoreService implements JobOutputRepatriationService{

    private final OutputProductProvisioner outputProductProvisioner;

    @Override
    public List<Path> repatriateOutputFiles(Job job, String outputId, Path filePath, InputStream fileInputStream) throws IOException {
        if (isStageOutDir(job, outputId)) {
            return stageOutRepatriation(job, outputId, fileInputStream);
        }
        return Collections.singletonList(legacyRepatriation(job, filePath, fileInputStream));
    }

    private boolean isStageOutDir(Job job, String outputId) {
        List<Parameter> dataOutputs = job.getConfig().getService().getServiceDescriptor().getDataOutputs();
        if (dataOutputs == null) {
            return false;
        }

        return dataOutputs.stream()
                .filter(dataOutput -> outputId.equals(dataOutput.getId()))
                .anyMatch(dataOutput -> dataOutput.getPlatformMetadata() != null
                        && dataOutput.getPlatformMetadata().containsKey("type")
                        && "STAC".equalsIgnoreCase(dataOutput.getPlatformMetadata().get("type")));
    }

    private List<Path> stageOutRepatriation(Job job, String outputId, InputStream inputStream) throws IOException {
        Path tempUnzipFileDir = Files.createTempDirectory("temp-unzip-" + outputId);
        try {
            return repatriateStacArchive(job, outputId, unzipStacArchive(inputStream, tempUnzipFileDir));
        } finally {
            cleanUpStacTempFolder(tempUnzipFileDir);
        }
    }

    private Path legacyRepatriation(Job job, Path filePath, InputStream fileInputStream) throws IOException {
        Path outputPath = provisionOutputPath(job, filePath);
        writeFileToOutputPath(job, fileInputStream, outputPath);
        return outputPath;
    }

    private List<Path> repatriateStacArchive(Job job, String outputId, List<Path> unzippedStacArchive) throws IOException {
        List<Path> repatriatedFiles = new ArrayList<>();
        for (Path fileToRepatriate : unzippedStacArchive) {
            // in case the behavior wants to be extended also to zip files containing directories, the getFileName is not enough
            Path outputPath = provisionOutputPath(job, Paths.get(outputId).resolve(fileToRepatriate.getFileName()));
            try (InputStream fileStream = Files.newInputStream(fileToRepatriate)) {
                writeFileToOutputPath(job, fileStream, outputPath);
            }
            repatriatedFiles.add(outputPath);
        }
        return repatriatedFiles;
    }

    private List<Path> unzipStacArchive(InputStream inputStream, Path tempUnzipFileDir) throws IOException {
        ZipUtils.unzipInFolder(inputStream, tempUnzipFileDir);
        try (Stream<Path> paths = Files.walk(tempUnzipFileDir)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    private Path provisionOutputPath(Job job, Path filePath) throws IOException {
        OutputProductMetadata outputProductMetadata = OutputProductMetadata.builder().jobId(job.getExtId()).build();
        return outputProductProvisioner.provisionNewOutputProduct(outputProductMetadata, filePath.toString());
    }

    private void writeFileToOutputPath(Job job, InputStream fileInputStream, Path outputPath) throws IOException {
        LOG.info("Writing output file for job {}: {}", job.getExtId(), outputPath);
        try (BufferedOutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(outputPath, CREATE, TRUNCATE_EXISTING, WRITE))) {
            IOUtils.copyLarge(fileInputStream, outputStream);
        }
    }

    private void cleanUpStacTempFolder(Path tempUnzipFileDir) {
        try (Stream<Path> toDeleteFolder = Files.walk(tempUnzipFileDir)) {
            for (Path fileToDelete : toDeleteFolder.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Files.deleteIfExists(fileToDelete);
            }
            Files.deleteIfExists(tempUnzipFileDir);
        } catch (IOException e) {
            LOG.error("Error while cleaning up temporary STAC folder", e);
        }
    }
}
