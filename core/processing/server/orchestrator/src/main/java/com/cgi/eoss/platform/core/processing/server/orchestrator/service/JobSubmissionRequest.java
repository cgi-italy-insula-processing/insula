package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper object for job submit spec encapsulating job inputs and resource requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSubmissionRequest {

    private Job job;

    private JobInputs jobInputs;

    private JobResourceRequirement jobResourceRequirement;

}
