package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInput;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputs;

import java.util.List;

/**
 * Handles preparation of job inputs before a job is launched:
 * exploding container inputs into individual product URIs and resolving input URIs.
 */
public interface JobInputsProcessor {

    /**
     * Explodes and splits the input identified by {@code parallelInputsKey}.
     *
     * @param jobInputs         the job inputs holding the input to process
     * @param parallelInputsKey the id of the input to split and explode
     * @return the exploded and split input
     */
    JobInput splitAndExplodeInputs(JobInputs jobInputs, String parallelInputsKey);

    /**
     * Explodes all job inputs.
     *
     * @param jobInputs the job inputs to explode
     * @return the exploded job inputs
     */
    JobInputs explodeInputs(JobInputs jobInputs);

    /**
     * Resolves the given job inputs.
     *
     * @param jobInputs the job inputs to resolve
     * @return the resolved job inputs
     */
    JobInputs resolveJobInputs(JobInputs jobInputs);

    /**
     * Resolves a single input URI string into its actual URIs.
     *
     * @param uriString the input URI string to resolve
     * @param username  the username of the job owner
     * @return a list containing the resolved URIs, or the original string if it cannot be resolved
     */
    List<String> resolveUri(String uriString, String username);
}