package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import java.util.HashMap;
import java.util.Map;

import com.cgi.eoss.platform.core.processing.server.orchestrator.utils.CoreModelToGrpcUtils;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import lombok.RequiredArgsConstructor;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.Kind;
import com.cgi.eoss.platform.rpc.StopJob;
import com.google.common.collect.ImmutableMap;

import lombok.extern.log4j.Log4j2;

/**
 * Provides functionality to submit StopJob requests to a queue
 */
@Log4j2
@RequiredArgsConstructor
public class PlatformJobStopRequestSubmitter {

    private final QueueService queueService;

    /**
     * Sends a message representing a request to stop a job to the job stop event's queue
     *
     * @param job the {@link Job} to stop
     * @throws IllegalArgumentException if the Job's service type is unrecognized
     */
    public void stopJob(Job job) {
        PlatformService service = job.getConfig().getService();
        switch (service.getType()) {
            case APPLICATION:
                stopDockerJob(job, Kind.INTERACTIVE);
                return;
            case PROCESSOR:
            case PARALLEL_PROCESSOR:
            case BULK_PROCESSOR:
                stopDockerJob(job, Kind.WORKFLOW);
                return;
        }
        throw new IllegalArgumentException(String.format("Unrecognized service type %s for service %s ", service.getType(), service.getName()));
    }

    private void stopDockerJob(Job job, Kind kind) {
        StopJob stopJob = StopJob.newBuilder().setJob(CoreModelToGrpcUtils.toRpcJob(job)).setKind(kind).build();
        enqueueJobMessage(ProcessingCoreQueueNames.JOB_STOP_REQUESTS, job.getId(), ImmutableMap.of("workerId", job.getWorkerId()), stopJob, 1);
        LOG.info("Stop requested for job {}", job.getExtId());
    }

    private void enqueueJobMessage(String queueName, Long jobId, Map<String, String> additionalHeaders, Object message, int priority) {
        HashMap<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("jobId", String.valueOf(jobId));
        messageHeaders.putAll(additionalHeaders);
        queueService.sendObject(queueName, messageHeaders, message, priority);
        LOG.info("Sent message for job {} to queue {}", jobId, queueName);
    }
}