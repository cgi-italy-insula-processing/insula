package com.cgi.eoss.platform.core.processing.inputdownloader;

import java.io.IOException;

/**
 * Defines a runner responsible for triggering the application execution
 * based on the current configuration.
 */
public interface InputDownloaderRunner {

    /**
     * Executes the application.
     *
     * @throws IOException if an I/O error occurs during the download execution
     */
    void run() throws IOException;
}