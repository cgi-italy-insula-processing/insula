package com.cgi.eoss.platform.core.processing.inputdownloader;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Class that triggers the execution of the Input Downloader when it is running in {@code processing-core} mode.
 */
@AllArgsConstructor
public class DefaultInputDownloaderRunner implements InputDownloaderRunner{

    private final EnvironmentService environmentService;
    private final Path basePath;
    private final String jobId;
    private final String jobOwner;

    @Override
    public void run() throws IOException {
        runCommand();
    }

    private void runCommand() throws IOException {
        environmentService.prepareEnvironment(jobId, basePath, jobOwner);
    }
}
