package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import java.time.LocalDateTime;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.protobuf.Timestamp;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Core {@link WorkerJobUpdatesManager}: drives a job through its life cycle (status, phase and timings) and delegates
 * its output processing to the {@link OutputProcessor}.
 */
@Log4j2
@AllArgsConstructor
public class CoreWorkerJobUpdatesManager implements WorkerJobUpdatesManager {

    private final JobDataService jobDataService;
    private final OutputProcessor outputProcessor;

    @Override
    public void onJobDataFetchingStarted(Job job, String workerId) {
        LOG.info("Downloading input data for {}", job.getExtId());
        job.setWorkerId(workerId);
        // Update the start time if this is the first job execution
        if (job.getStartTime() == null) {
            job.setStartTime(LocalDateTime.now());
        }
        job.setStatus(Job.Status.RUNNING);
        job.setPhase(JobStep.DATA_FETCH);
        job.setStage(JobStep.DATA_FETCH.getText());
        jobDataService.save(job);
    }

    @Override
    public void onJobDataFetchingCompleted(Job job) {
        LOG.info("Launching docker container for job {}", job.getExtId());
    }

    /**
     * Handle the start of the processing for a job.
     *
     * @param job the job whose processing started
     * @param timestamp the time at which the processing started
     * @param backendServiceLocation not needed in the core life cycle
     */
    @Override
    public void onJobProcessingStarted(Job job, Timestamp timestamp, String backendServiceLocation) {
        if (job.getPhase().equals(JobStep.PROCESSING)) {
            return;
        }
        PlatformService service = job.getConfig().getService();
        LOG.info("Job {} ({}) launched for service: {}", job.getId(), job.getExtId(), service.getName());
        job.setStatus(Status.RUNNING);
        job.setPhase(JobStep.PROCESSING);
        job.setStage(JobStep.PROCESSING.getText());
        jobDataService.save(job);
    }

    @Override
    public void onJobProcessingCompleted(Job job, Timestamp timestamp) {
        if (job.getPhase().equals(JobStep.OUTPUT_LIST)) {
            return;
        }
        if (job.getPhase() == JobStep.DATA_FETCH) {
            onJobError(job, "Job stopped during data fetching");
            return;
        }
        job.setPhase(JobStep.OUTPUT_LIST);
        job.setStage(JobStep.OUTPUT_LIST.getText());
        job.setEndTime(LocalDateTime.now()); // End time is when processing ends
        jobDataService.save(job);
        try {
            outputProcessor.processOutputs(job);
            job.setStatus(Job.Status.COMPLETED);
            jobDataService.save(job);
            completeParentJobIfAllSubJobsCompleted(job);
        } catch (Exception e) {
            onJobError(job, e);
        }
    }

    /**
     * No-op in the core life cycle.
     */
    @Override
    public void onJobHeartbeat(Job job, Timestamp timestamp) {
    }

    @Override
    public void onJobError(Job job, String description) {
        // Ignore errors happening after completion
        if (job.getStatus() == Status.COMPLETED) {
            LOG.error("Error in Job {} - ignoring as job is complete: {}", job.getExtId(), description);
            return;
        }
        LOG.error("Error in Job {}: {}", job.getExtId(), description);
        endJobWithError(job);
    }

    @Override
    public void onJobError(Job job, Throwable throwable) {
        LOG.error("Error in Job {}", job.getExtId(), throwable);
        endJobWithError(job);
    }

    private void completeParentJobIfAllSubJobsCompleted(Job job) {
        if (job.getParentJob() == null) {
            return;
        }
        Job parentJob = jobDataService.refreshFull(job.getParentJob());
        if (jobDataService.allSubJobsCompleted(parentJob)) {
            completeParentJob(parentJob);
        }
    }

    private void completeParentJob(Job parentJob) {
        parentJob.setStatus(Job.Status.COMPLETED);
        parentJob.setPhase(JobStep.OUTPUT_LIST);
        parentJob.setStage(JobStep.OUTPUT_LIST.getText());
        parentJob.setEndTime(LocalDateTime.now());
        jobDataService.save(parentJob);
    }

    private void endJobWithError(Job job) {
        job.setStatus(Job.Status.ERROR);
        job.setEndTime(LocalDateTime.now());
        jobDataService.save(job);
        if (job.getParentJob() != null) {
            Job parentJob = job.getParentJob();
            parentJob.setStatus(Job.Status.ERROR);
            jobDataService.save(parentJob);
        }
    }
}
