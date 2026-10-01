package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.CancelJobRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.StopJobRequest;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.utils.PageableCollection;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Provides methods to stop or cancel Jobs' executions
 */
@RequiredArgsConstructor
@Log4j2
public class PlatformJobStopper {

    private final JobDataService jobDataService;
    private final PlatformJobStopRequestSubmitter platformJobStopRequestSubmitter;
    private final QueueService queueService;
    private final Integer subJobsPageSize;
    private final String pendingJobsQueueName;
    private final String waitingJobsQueueName;

    /**
     * Stops a running job identified by the given request.
     *
     * @param stopJobRequest the request identifying which job to stop
     * @throws PlatformEntityNotFoundException if no job exists with the given ID
     */
    public void stopJob(StopJobRequest stopJobRequest) {
        String intJobId = stopJobRequest.getIntJobId();
        Job job = jobDataService.getById(Long.parseLong(intJobId))
                .orElseThrow(() -> new PlatformEntityNotFoundException("Failed to load Job with ID: " + intJobId));
        platformJobStopRequestSubmitter.stopJob(job);
        LOG.info("Successfully stopped job {}", job.getExtId());
    }

    /**
     * Cancels the job identified by the given request. If the job is a parent
     * job, all of its sub-jobs are canceled as well.
     *
     * @param cancelJobRequest the request identifying which job to cancel
     * @throws PlatformEntityNotFoundException if no job exists with the given ID
     */
    public void cancelJob(CancelJobRequest cancelJobRequest) {
        String intJobId = cancelJobRequest.getIntJobId();
        Job job = jobDataService.getById(Long.parseLong(intJobId))
                .orElseThrow(() -> new PlatformEntityNotFoundException("Failed to load Job with ID: " + intJobId));

        if (job.isParent()) {
            cancelSubJobs(job);
        } else {
            cancelJob(job);
        }
    }

    private void cancelSubJobs(Job parentJob) {
        List<Long> subJobIds = jobDataService.getSubJobIds(parentJob);
        PageableCollection<Long> subJobIdsPage = new PageableCollection<>(subJobIds, this.subJobsPageSize);
        while (subJobIdsPage.hasNext()) {
            jobDataService.findByIds(subJobIdsPage.next()).forEach(
                    this::cancelJob
            );
        }
    }

    private void cancelJob(Job job) {
        LOG.info("Cancelling job with id {}", job.getId());

        Job.Status status = job.getStatus();
        if (status.equals(Job.Status.CANCELLED)) {
            LOG.info("Job {} was already cancelled", job.getId());
            return;
        }
        if (jobHasNotStartedYet(status)) {

            if (job.getStatus().equals(Job.Status.PENDING)) {
                queueService.receiveSelectedObjectNoWait(pendingJobsQueueName, "jobId = '" + job.getId() + "'");
            } else if (job.getStatus().equals(Job.Status.WAITING)) {
                queueService.receiveSelectedObjectNoWait(waitingJobsQueueName, "jobId = '" + job.getId() + "'");
            }

            job.setStatus(Job.Status.CANCELLED);
            jobDataService.save(job);
        }
    }

    private boolean jobHasNotStartedYet(Job.Status status) {
        return status == Job.Status.CREATED || status == Job.Status.WAITING || status == Job.Status.PENDING || status == Job.Status.CONDITION_WAIT;
    }

}