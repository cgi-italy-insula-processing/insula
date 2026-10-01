package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInput;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class that processes Job Input objects of STAC type.
 */
@Slf4j
@AllArgsConstructor
public class StacInputsService {

    private final ObjectMapper objectMapper;
    private final AmazonS3 s3Client;
    private final StacItemsS3Bucket stacItemsS3Bucket;
    private final List<StacDownloader> stacDownloaders;

    /**
     * Explodes a Job Input of STAC type by downloading the STAC document and uploading it into an S3 bucket,
     * then setting its internal reference and features to the Job Input object.
     * @param jobInput the Job Input object.
     * @param jobId the ext ID of the job the input belongs to.
     * @param userName the name of the user the job belongs to.
     * @return the Job Input object with its internal reference and list of Stac Features set.
     */
    public JobInput explodeStacItems(JobInput jobInput, String jobId, String userName) {
        if (jobInput.isExploded()) {
            return jobInput;
        }
        String stacInput = jobInput.getValues().get(0);
        URL stacDocumentUrl = toURL(stacInput);
        StacDocument stacDocument = downloadStacDocument(stacDocumentUrl, userName);
        URL internalReference = storeInBucketAndRetrieveUrl(stacItemsS3Bucket.getStacItemsS3BucketName(),
                buildS3StacDocumentKey(jobInput.getId(), jobId), stacDocument);
        List<StacDocument.StacItem> stacItems = stacDocument.getFeatures();
        return buildJobInputWith(jobInput, explodeStacValues(stacDocumentUrl, stacItems), internalReference, stacItems);
    }

    private StacDocument downloadStacDocument(URL stacURL, String userName) {
        for (StacDownloader stacDownloader : stacDownloaders) {
            if (stacDownloader.supports(stacURL)) {
                try {
                    return stacDownloader.download(stacURL, userName);
                } catch (Exception e) {
                    LOG.error("Downloader {} failed to download STAC Document from {} - reason: {}",
                            stacDownloader.getClass().getCanonicalName(), stacURL, e.getMessage());
                }
            }
        }
        throw new IllegalStateException("Unable to download STAC Document from " + stacURL);
    }

    private URL storeInBucketAndRetrieveUrl(String bucketName, String itemKey, StacDocument stacDocument) {
        ensureBucketExists(bucketName);
        try {
            s3Client.putObject(bucketName, itemKey, objectMapper.writeValueAsString(stacDocument));
        } catch (AmazonServiceException | JsonProcessingException e) {
            LOG.error("Could not write Stac Document to S3: {}", e.getMessage());
            throw new IllegalStateException(e);
        }
        return s3Client.getUrl(bucketName, itemKey);
    }

    private void ensureBucketExists(String bucketName) {
        HeadBucketRequest headBucketRequest = new HeadBucketRequest(bucketName);
        try {
            s3Client.headBucket(headBucketRequest);
        } catch (AmazonServiceException e) {
            handleBucketCreationIfNotFound(e, bucketName);
        }
    }

    private void handleBucketCreationIfNotFound(AmazonServiceException e, String bucketName) {
        if (e.getStatusCode() == 404) {
            LOG.warn("Bucket {} does not exist, creating it", bucketName);
            createS3Bucket(bucketName);
            return;
        }
        LOG.error("Error in checking bucket existence {}", e.getMessage());
        throw new IllegalStateException(e);
    }

    private void createS3Bucket(String bucketName) {
        try {
            s3Client.createBucket(bucketName);
        } catch (AmazonServiceException e) {
            LOG.error("Error in creating bucket {}", e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private List<String> explodeStacValues(URL stacDocumentUrl, List<StacDocument.StacItem> stacItems) {
        return stacItems.stream()
                .map(stacItem -> stacDocumentUrl.toString().concat("#" + stacItem.getId()))
                .collect(Collectors.toList());
    }

    private static String buildS3StacDocumentKey(String inputId, String jobId) {
        return jobId.concat("/" + inputId).concat("/catalog.json");
    }

    private static JobInput buildJobInputWith(JobInput jobInput, List<String> explodedValues,
                                              URL stacDocumentUrl, List<StacDocument.StacItem> contents) {
        return jobInput.toBuilder().values(explodedValues).internalReference(stacDocumentUrl).contents(contents).build();
    }

    private static URL toURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            LOG.error("Stac Document URL not valid: {}", url);
            throw new IllegalArgumentException("Stac Document URL not valid: " + url);
        }
    }
}