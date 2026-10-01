package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.rpc.JobSpec;

/**
 * <p>
 * Produces the gRPC {@link JobSpec} for a submitted job. Each implementation is responsible for one family of
 * {@link PlatformService} types and reports which services it handles via {@link #supports(PlatformService)},
 * so that a driver can dispatch a submission to the right producer without knowing the service types.
 * </p>
 */
public interface JobSpecProducer {

    /**
     * Builds the spec for the submitted job, marks the job as pending and persists it.
     *
     * @param jobSubmissionRequest the request holding the job, its inputs and its resource requirements
     * @return the built {@link JobSpec}
     * @throws PlatformEntityNotFoundException if a user mount
     *         referenced by the service cannot be found
     */
    JobSpec produceJobSpec(JobSubmissionRequest jobSubmissionRequest);

    /**
     * Check if the producer can create the spec for the given service.
     *
     * @param service the service the submitted job would run
     * @return {@code true} if this producer handles the service, {@code false} otherwise
     */
    boolean supports(PlatformService service);
}
