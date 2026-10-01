package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Repatriates a job's output objects from the object storage into the platform output product storage.
 */
@Log4j2
@AllArgsConstructor
public class JobOutputsRepatriationService {

    private final JobOutputsRepatriationServiceProperties jobOutputsRepatriationServiceProperties;
    private final AmazonS3 amazonS3;
    private final JobOutputRepatriationService jobOutputRepatriationService;

    /**
     * Repatriate all the job's output objects from the object storage.
     *
     * @param job the job whose output objects must be repatriated
     * @return the repatriated outputs, each pairing a service output identifier with the repatriated file paths
     * @throws IOException if an output object cannot be read from the object storage or repatriated
     */
    public List<RepatriatedOutput> repatriate(Job job) throws IOException {
        Multimap<String, String> outputsByRelativeKeys = listObjects(job);
        return repatriateOutputObjects(job, outputsByRelativeKeys);
    }

    private Multimap<String, String> listObjects(Job job) {
        String jobOutputsBucketName = jobOutputsRepatriationServiceProperties.getJobOutputsBucketName();
        Multimap<String, String> outputsByRelativeKeys = ArrayListMultimap.create();
        if (!amazonS3.doesBucketExist(jobOutputsBucketName)) {
            return outputsByRelativeKeys;
        }
        List<String> relativeKeys = new ArrayList<>();
        String rootKey = String.valueOf(job.getExtId());
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
        listObjectsRequest.setBucketName(jobOutputsBucketName);
        listObjectsRequest.setPrefix(rootKey + "/");
        listObjectsRequest.setEncodingType("url");
        ObjectListing objectListing;
        do {
            objectListing = amazonS3.listObjects(listObjectsRequest);
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                String objectKey = objectSummary.getKey();
                relativeKeys.add(Paths.get(rootKey).relativize(Paths.get(objectKey)).toString());

            }
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());

        PlatformService service = job.getConfig().getService();

        Set<String> expectedServiceOutputIds;

        if (service.getServiceDescriptor().getDataOutputs() == null) {
            expectedServiceOutputIds = Collections.emptySet();
        } else {
            expectedServiceOutputIds = service.getServiceDescriptor().getDataOutputs()
                    .stream().map(PlatformServiceDescriptor.Parameter::getId).collect(toSet());
        }
        extractRelativeKeys(outputsByRelativeKeys, relativeKeys, expectedServiceOutputIds);
        return outputsByRelativeKeys;
    }

    private void extractRelativeKeys(Multimap<String, String> outputsByRelativeKeys, List<String> relativeKeys, Set<String> expectedServiceOutputIds) {
        for (String expectedOutputId : expectedServiceOutputIds) {
            List<String> relativeKeyValues = relativeKeys.stream()
                    .filter(path -> path.startsWith(expectedOutputId + "/"))
                    .collect(Collectors.toList());
            // TODO Check against user defined min/max occurs
            if (!relativeKeyValues.isEmpty()) {
                outputsByRelativeKeys.putAll(expectedOutputId, relativeKeyValues);
            } else {
                LOG.info("Service defined output with ID '{}' but no matching directory was found in the job outputs", expectedOutputId);
            }
        }
    }

    private List<RepatriatedOutput> repatriateOutputObjects(Job job, Multimap<String, String> outputsByRelativeKeys) throws IOException {
        String jobOutputsBucketName = jobOutputsRepatriationServiceProperties.getJobOutputsBucketName();
        List<RepatriatedOutput> repatriatedOutputs = new ArrayList<>();
        for (String outputId : outputsByRelativeKeys.keySet()) {
            for (String relativeKey : outputsByRelativeKeys.get(outputId)) {
                repatriatedOutputs.add(
                    new RepatriatedOutput(outputId, repatriateOutputObject(job, outputId, jobOutputsBucketName, relativeKey))
                );
            }
        }
        return repatriatedOutputs;
    }

    private List<Path> repatriateOutputObject(Job job, String outputId, String bucket, String relativeKey) throws IOException {
        String key = job.getExtId() + "/" + relativeKey;
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
        LOG.info("Retrieving object {} - exists? {}", key, amazonS3.doesObjectExist(bucket, key));
        try (S3Object object = amazonS3.getObject(getObjectRequest); InputStream inputStream = object.getObjectContent()) {
            return jobOutputRepatriationService.repatriateOutputFiles(job, outputId, Paths.get(relativeKey), inputStream);
        }
    }
}
