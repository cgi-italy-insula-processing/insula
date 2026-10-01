package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacInputsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job inputs processor that only handles inputs of type STAC.
 * Non-STAC inputs are returned untouched and URI resolution is a no-op.
 */
@Log4j2
@RequiredArgsConstructor
public class StacJobInputsProcessor implements JobInputsProcessor {

    private final StacInputsService stacInputsService;

    /**
     * Explodes and splits a single input when it is of type STAC;
     * for any other input type no transformation is performed and the input is returned unchanged.
     *
     * @param jobInputs         the job inputs holding the input to process
     * @param parallelInputsKey the id of the input to split and explode
     * @return the exploded input if of type STAC, otherwise the input unchanged
     */
    @Override
    public JobInput splitAndExplodeInputs(JobInputs jobInputs, String parallelInputsKey) {
        return explodeInputs(jobInputs.get(parallelInputsKey), jobInputs.getJobId(), jobInputs.getUserName());
    }

    /**
     * Explodes all job inputs: each input of type STAC is exploded;
     * for any other input type no transformation is performed and the input is returned unchanged.
     *
     * @param jobInputs the job inputs to explode
     * @return the job inputs with STAC inputs exploded and all others unchanged
     */
    @Override
    public JobInputs explodeInputs(JobInputs jobInputs) {
        Map<String, JobInput> explodedInputsMap = new HashMap<>();

        jobInputs.getValuesMap().asMap().forEach((inputId, collectionInput) -> {
            JobInput jobInputExploded = explodeInputs(jobInputs.get(inputId), jobInputs.getJobId(), jobInputs.getUserName());
            explodedInputsMap.put(inputId, jobInputExploded);
        });

        return jobInputs.toBuilder()
                .inputs(explodedInputsMap)
                .build();
    }

    /**
     * Returns the given job inputs unchanged without performing any resolution.
     *
     * @param jobInputs the job inputs
     * @return the same job inputs, unchanged
     */
    @Override
    public JobInputs resolveJobInputs(JobInputs jobInputs) {
        return jobInputs;
    }

    /**
     * Returns the input URI string unchanged, wrapped in a single-element list without performing any resolution.
     *
     * @param uriString the input URI string
     * @param username  the username of the job owner
     * @return a single-element list containing the input URI string unchanged
     */
    @Override
    public List<String> resolveUri(String uriString, String username) {
        return Collections.singletonList(uriString);
    }

    private JobInput explodeInputs(JobInput jobInput, String jobId, String userName) {
        if (JobInput.Type.STAC.equals(jobInput.getType())) {
            return stacInputsService.explodeStacItems(jobInput, jobId, userName);
        }
        return jobInput;
    }
}