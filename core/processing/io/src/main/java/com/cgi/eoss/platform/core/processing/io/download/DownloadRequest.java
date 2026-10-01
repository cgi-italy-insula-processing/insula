package com.cgi.eoss.platform.core.processing.io.download;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.net.URI;
import java.nio.file.Path;

/**
 * Class that holds the details of a download request, which consists of the uri of the product to be downloaded,
 * the target folder where the downloaded product will be copied,
 * and the subsetting information, indicating a subset of the product.
 * The subsetting is not a mandatory field and can be null.
 */
@AllArgsConstructor
@Value
public class DownloadRequest {

    private final URI downloadUri;

    private final Path downloadFolder;

    private final Subsetting subsetting;
    private final String userUuid;

    /**
     * Creates an instance of DownloadRequest with the provided argument values. All other class attributes are set to the default values.
     * @param downloadUri the URI where from we will download the files.
     * @param downloadFolder the folder where files will be downloaded.
     * @param subsetting some subsetting requested by specific downloads.
     */
    public DownloadRequest(URI downloadUri, Path downloadFolder, Subsetting subsetting) {
        this.downloadUri = downloadUri;
        this.downloadFolder = downloadFolder;
        this.subsetting = subsetting;
        this.userUuid = null;
    }
}