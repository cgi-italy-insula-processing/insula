package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;

/**
 * Service interface for managing and validating job-related resources.
 */
public interface JobResourceManagementService {

    /**
     * Validates the resources required by a platform service.
     *
     * @param user                     the user whose available resources should be checked
     * @param platformServiceResources the resource requirements of the service
     */
    void validateResourceRequest(User user, PlatformServiceResources platformServiceResources);

    /**
     * Evaluates the resources required for job based on resource requirements of the service.
     *
     * @param user                     the user whose storage requirements should be evaluated
     * @param platformServiceResources the resource requirements of the service
     * @return JobResourceRequirement representing the user's allowed resources
     */
    JobResourceRequirement evaluateResourceRequest(User user, PlatformServiceResources platformServiceResources);
}
