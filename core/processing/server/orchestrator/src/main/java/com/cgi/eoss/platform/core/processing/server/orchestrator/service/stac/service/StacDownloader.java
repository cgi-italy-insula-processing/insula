package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;

import java.net.URL;

/**
 * Downloads a STAC Document from a URL.
 * Each implementation handles a distinct set of URLs.
 */
public interface StacDownloader {

    /**
     * Evaluates the given URL to determine whether this downloader can handle it.
     *
     * @param url the URL of the STAC Document.
     * @return whether the URL falls within the scope of this downloader.
     */
    boolean supports(URL url);

    /**
     * Downloads the STAC Document located at the given URL.
     *
     * @param url the URL of the STAC Document.
     * @param username the identifier of the user requesting the download.
     * @return the downloaded STAC Document.
     * @throws IllegalStateException if the download fails.
     */
    StacDocument download(URL url, String username);
}