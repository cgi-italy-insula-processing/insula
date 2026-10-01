package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacAssetRelocator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacItemDeserializer;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service responsible to retrieve job output files from the file system
 */
@AllArgsConstructor
public class JobOutputsRetrievalService {

    private final JobDataService jobDataService;
    private final JobOutputLocator jobOutputLocator;
    private final StacItemDeserializer stacItemDeserializer;
    private final StacAssetRelocator stacAssetRelocator;
    private final URL baseUrl;

    /**
     * Retrieve the path to the requested job output file from the filesystem
     *
     * @param jobId The id of the job for which the output file was requested
     * @param outputId The id of the output of the job for which the output file was requested
     * @param filename The name of the file to retrieve
     *
     * @return The path to the requested job output file
     */
    public Path retrieveOutputFile(Long jobId, String outputId, String filename) {
        return jobOutputLocator.getOutputFile(retrieveJobOrThrow(jobId).getExtId(), outputId, filename);
    }

    /**
     * Retrieve the requested job outputs of type STAC as a STAC Collection.
     * For non STAC outputs, a minimal STAC Item is synthesized for each output file
     * In any case, the assets of the STAC Items link to the URLs the files can be downloaded from.
     *
     * @param jobId The id of the job for which the output was requested
     * @param outputId The id of the output of the job that was requested
     *
     * @return The STAC Collection referencing the various job output files
     */
    public StacDocument retrieveAsStacCollection(Long jobId, String outputId) {
        List<StacItem> features = new ArrayList<>();
        for (Job job : jobOrSubJobs(retrieveJobOrThrow(jobId))) {
            features.addAll(itemsOf(job, outputId));
        }

        StacDocument stacCollection = new StacDocument();
        stacCollection.setType("FeatureCollection");
        stacCollection.setFeatures(features);
        return stacCollection;
    }

    private Job retrieveJobOrThrow(Long jobId) {
        return jobDataService.getById(jobId)
                .orElseThrow(() -> new PlatformEntityNotFoundException("Job with id " + jobId + " not found"));
    }

    private List<Job> jobOrSubJobs(Job job) {
        if (!job.isParent()) {
            return Collections.singletonList(job);
        }
        List<Long> subJobIds = jobDataService.getSubJobIds(job);
        return subJobIds.isEmpty() ? Collections.emptyList() : jobDataService.findByIds(subJobIds);
    }

    private List<StacItem> itemsOf(Job job, String outputId) {
        List<Path> jobOutputFiles = jobOutputLocator.getOutputFiles(job.getExtId(), outputId);
        List<StacItem> items;

        if (isStacOutput(job.getConfig(), outputId)) {
            items = readStacItems(jobOutputFiles);
        } else {
            items = defaultStacItems(job.getExtId(), outputId, jobOutputFiles);
        }

        stacAssetRelocator.relocateAssets(items,
                jobOutputUrl(job.getId(), outputId) + "?filename=" + StacAssetRelocator.FILE_NAME_PLACEHOLDER);

        return items;
    }

    private static boolean isStacOutput(JobConfig jobConfig, String outputId) {
        return serviceOutputParameters(jobConfig)
                .stream()
                .filter(output -> outputId.equals(output.getId()))
                .anyMatch(JobOutputsRetrievalService::isStacParameter);
    }

    private static List<PlatformServiceDescriptor.Parameter> serviceOutputParameters(JobConfig jobConfig) {
        return Optional.of(jobConfig)
                .map(JobConfig::getService)
                .map(PlatformService::getServiceDescriptor)
                .map(PlatformServiceDescriptor::getDataOutputs)
                .orElse(Collections.emptyList());
    }

    private static boolean isStacParameter(PlatformServiceDescriptor.Parameter output) {
        Map<String, String> platformMetadata = output.getPlatformMetadata();
        return platformMetadata != null && "STAC".equalsIgnoreCase(platformMetadata.get("type"));
    }

    private List<StacItem> readStacItems(List<Path> jobOutputFiles) {
        List<StacItem> items = new ArrayList<>();
        for (Path stacItemFile : stacItemFiles(jobOutputFiles)) {
            stacItemDeserializer.readItem(stacItemFile)
                    .ifPresent(items::add);
        }
        return items;
    }

    private static List<Path> stacItemFiles(List<Path> files) {
        return files.stream()
                .filter(file -> file.getFileName().toString().endsWith(".json"))
                .collect(Collectors.toList());
    }

    private List<StacItem> defaultStacItems(String jobExtId, String outputId, List<Path> jobOutputFiles) {
        Path outputFolder = jobOutputLocator.getOutputFolder(jobExtId, outputId);
        List<StacItem> items = new ArrayList<>();
        for (Path jobOutputFile : jobOutputFiles) {
            String filePath = outputFolder.relativize(jobOutputFile).toString();
            items.add(stacItemDeserializer.defaultItem(jobExtId + "_" + outputId + "_" + filePath,
                    lastModified(jobOutputFile), filePath));
        }
        return items;
    }

    private URI jobOutputUrl(Long jobId, String outputId) {
        String outputUrl = baseUrl + "/jobs/" + jobId + "/outputs/" + outputId;
        try {
            return URI.create(outputUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid job output URL: " + outputUrl, e);
        }
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the modification time of job output file " + file, e);
        }
    }

}
