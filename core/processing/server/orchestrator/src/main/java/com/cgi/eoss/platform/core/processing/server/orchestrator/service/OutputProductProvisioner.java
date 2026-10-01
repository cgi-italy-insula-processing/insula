package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.internal.OutputProductMetadata;

import java.io.IOException;
import java.nio.file.Path;

/**
 * <p>Interface that provides thin provisioning of storage paths for new output product files: resolves the
 * destination path and ensures its parent directories exist, without creating or writing the file itself.</p>
 */
public interface OutputProductProvisioner {

    /**
     * Resolves the storage path for a new output product file, creating its parent directories.
     *
     * @param outputProductMetadata the metadata of the output product being provisioned; its job id determines the
     *                              storage location under which the file path is resolved
     * @param filename              the name (or relative path) of the file to provision under the job's storage location
     * @return the resolved path where the new output product file can be written
     * @throws IOException if the parent directories of the resolved path cannot be created
     */
    Path provisionNewOutputProduct(OutputProductMetadata outputProductMetadata, String filename) throws IOException;

}
