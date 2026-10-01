package com.cgi.eoss.platform.core.processing.outputuploader;

import java.io.IOException;

/**
 * Defines a runner responsible for triggering the execution of the application
 * based on the current configuration.
 */
public interface OutputUploaderRunner {

    /**
     * Executes the application.
     *
     * @throws IOException if directories or files cannot be created
     *                      on the file system
     */
    void run() throws IOException;
}
