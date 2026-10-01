package com.cgi.eoss.platform.core.processing.io.download;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * <p>Facade to {@link Downloader} implementations. Offers retry logic across multiple providers for a given URI
 * scheme.</p>
 */
public interface DownloaderFacade {

    Path download(DownloadRequest downloadRequest);

    Map<URI, Path> download(List<DownloadRequest> downloadRequests);

    boolean isSupportedProtocol(String scheme);

    void cleanUp(URI uri);

}
