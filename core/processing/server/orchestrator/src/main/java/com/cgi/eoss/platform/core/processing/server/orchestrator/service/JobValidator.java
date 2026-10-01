package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobValidationResult;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class JobValidator {

    private final List<JobValidationResultProducer> jobValidationResultProducers;

    /**
     * Enforces the validation for an actual job.
     *
     * @param job The job to validate
     * @param jobInputs The exploded inputs of the job
     * @return The result of the job validation.
     */
    public JobValidationResult validate(Job job, JobInputs jobInputs) {
        JobValidationResult result = new JobValidationResult(job, jobInputs, true, null);
        for (JobValidationResultProducer jobValidationResultProducer : jobValidationResultProducers) {
            result = result.and(jobValidationResultProducer::validate);
        }
        return result;
    }
}
