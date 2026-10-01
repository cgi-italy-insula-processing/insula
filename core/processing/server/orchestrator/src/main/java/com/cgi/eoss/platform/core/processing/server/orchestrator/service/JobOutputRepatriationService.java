package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * <p>Repatriates a job output: pulls the bytes from the given input stream into the platform output product storage and
 * returns the path(s) where the files have been written.</p>
 */
public interface JobOutputRepatriationService {


    /**
     * Repatriates the output(s) read from the given input stream into the platform output product storage, for a single output identifier.
     * <p>
     * For STAC stage-out outputs the input stream is a zip archive: it is unzipped and every entry (including the
     * STAC metadata json) is repatriated. For regular outputs the stream is treated as a single file.
     *
     * @param job             the job that produced the output, used to resolve the output product storage location
     * @param outputId        the identifier of the service output being repatriated; also determines whether the
     *                        output is a STAC stage-out (zip archive) or a regular single file
     * @param filePath        the relative path used to name the repatriated file in the output product storage
     *                        (used only for regular, non-stage-out outputs)
     * @param fileInputStream the stream to read the output bytes from; for STAC stage-out outputs this is a zip
     *                        archive. Owned by the caller, which is responsible for closing it
     * @return the path(s) where the files have been repatriated
     *
     * @throws IOException if reading the input stream, unzipping the archive, or writing the output files fails
     */
    List<Path> repatriateOutputFiles(Job job, String outputId, Path filePath, InputStream fileInputStream) throws IOException;

}
