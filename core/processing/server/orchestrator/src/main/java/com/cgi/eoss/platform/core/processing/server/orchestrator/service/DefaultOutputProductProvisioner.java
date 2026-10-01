package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.internal.OutputProductMetadata;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <p>Default implementation of {@link OutputProductProvisioner} that provisions output product paths under a single
 * base directory.</p>
 */
@Log4j2
public class DefaultOutputProductProvisioner implements OutputProductProvisioner {

    private final Path baseDir;

    /**
     * Creates a provisioner that resolves output product paths under the given base directory.
     *
     * @param baseDir the base directory under which output products are provisioned (one sub-directory per job id)
     */
    public DefaultOutputProductProvisioner(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public Path provisionNewOutputProduct(OutputProductMetadata outputProductMetadata, String filename) throws IOException {
        Path outputPath = baseDir.resolve(outputProductMetadata.getJobId()).resolve(filename);

        if (Files.exists(outputPath)) {
            LOG.warn("Found already-existing output product, may be overwritten: {}", outputPath);
        }

        Files.createDirectories(outputPath.getParent());
        return outputPath;
    }

}
