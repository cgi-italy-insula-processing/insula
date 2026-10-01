package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobValidationResult;

/** Job's and job inputs' validation interface. **/
public interface JobValidationResultProducer {

    /** Validates the job and it's relative inputs
     * @param job The job to validate
     * @param jobInputs The inputs relative to the job to validate
     * @return The result of job and relative inputs validation
     * **/
    JobValidationResult validate(Job job, JobInputs jobInputs);

    /**
     * Builds a successful validation result for the given job and inputs.
     * @param job The validated job
     * @param jobInputs The inputs relative to the validated job
     * @return A successful validation result
     * **/
    default JobValidationResult successfulValidation(Job job, JobInputs jobInputs) {
        return new JobValidationResult(job, jobInputs, true, null);
    }

    /**
     * Builds a failed validation result carrying the given error message.
     * @param job The validated job
     * @param jobInputs The inputs relative to the validated job
     * @param errorMessage The message describing the validation failure
     * @return A failed validation result
     * **/
    default JobValidationResult failedValidation(Job job, JobInputs jobInputs, String errorMessage) {
        return new JobValidationResult(job, jobInputs, false, errorMessage);
    }
}
