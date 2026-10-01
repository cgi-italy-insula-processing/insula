package com.cgi.eoss.platform.core.processing.server.orchestrator.model;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputs;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobValidationResultProducer;
import lombok.Data;

/** Represents the result of a job validation **/
@Data
public class JobValidationResult {

    private final Job job;
    private final JobInputs jobInputs;
    private final boolean isValid;
    private final String errorMessage;

    /**
     *
     * Enforces chaining validation for Jobs
     * @param jobValidationResultProducer an instance of a specific job validator
     * @return The result of the validation
     *
     * **/
    public JobValidationResult and(JobValidationResultProducer jobValidationResultProducer) {
        if (!isValid) {
            return this;
        }
        return jobValidationResultProducer.validate(job, jobInputs);
    }
}