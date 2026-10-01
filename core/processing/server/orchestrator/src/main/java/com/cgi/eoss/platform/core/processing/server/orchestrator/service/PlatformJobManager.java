package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchResponse;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Coordinates the launch of platform jobs by preparing the submission requests.
 */
@Log4j2
@AllArgsConstructor
public class PlatformJobManager {

    private final PlatformJobLauncherCore platformJobLauncherCore;
    private final PlatformJobSubmitter platformJobSubmitter;

    /**
     * Launches a job based on the provided job launch request. It submits the job(s) to the processing engine and returns a
     * response containing the job details.
     *
     * @param jobLaunchRequest the request containing the job launch details
     * @return a response containing the created job's details
     */
    public JobLaunchResponse launchJob(JobLaunchRequest jobLaunchRequest) {

        return buildJobLaunchResponse(
            submitJobs(
                platformJobLauncherCore.submitJob(jobLaunchRequest)
            )
        );
    }

    private Job submitJobs(List<JobSubmissionRequest> jobSubmissionRequests) {
        Job job = jobSubmissionRequests.get(0).getJob();
        if (PlatformService.Type.PARALLEL_PROCESSOR == job.getConfig().getService().getType()) {
            return submitParallelSubJobs(jobSubmissionRequests);
        }
        return submitSingleJob(jobSubmissionRequests.get(0));
    }

    private Job submitParallelSubJobs(List<JobSubmissionRequest> jobSubmissionRequests) {
        jobSubmissionRequests.forEach(platformJobSubmitter::submitJob);
        return jobSubmissionRequests.get(0).getJob().getParentJob();
    }

    private Job submitSingleJob(JobSubmissionRequest jobSubmissionRequest) {
        Job job = jobSubmissionRequest.getJob();
        try {
            platformJobSubmitter.submitJob(jobSubmissionRequest);
        } catch (Exception e) {
            LOG.error("Error submitting job {}", job.getExtId(), e);
            throw e;
        }
        return job;
    }

    private static JobLaunchResponse buildJobLaunchResponse(Job job) {

        JobLaunchResponse.JobLaunchResponseBuilder builder = JobLaunchResponse.builder()
            .jobId(job.getExtId())
            .intJobId(String.valueOf(job.getId()))
            .userId(job.getOwner().getName())
            .serviceId(job.getConfig().getService().getName());

        return builder.build();
    }

}
