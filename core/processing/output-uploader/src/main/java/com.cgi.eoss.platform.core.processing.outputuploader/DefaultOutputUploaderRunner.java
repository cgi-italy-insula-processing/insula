package com.cgi.eoss.platform.core.processing.outputuploader;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Class that triggers the execution of the application when tenancy support is disabled.
 */
@AllArgsConstructor
public class DefaultOutputUploaderRunner implements OutputUploaderRunner {

    private final IngestionService ingestionService;
    private final Path basePath;
    private final String jobId;
    private final Map<String, String> outputs;

    @Override
    public void run() throws IOException {
        ingestionService.uploadOutputs(basePath, jobId, outputs);
    }
}
