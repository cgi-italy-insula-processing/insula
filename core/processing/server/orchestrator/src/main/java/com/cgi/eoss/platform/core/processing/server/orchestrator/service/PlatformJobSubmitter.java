package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import com.cgi.eoss.platform.rpc.JobSpec;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Set;

@AllArgsConstructor
@Slf4j
public class PlatformJobSubmitter {

    private final Set<JobSpecProducer> jobSpecProducers;
    private final JobPriorityCalculator jobPriorityCalculator;
    private final QueueService queueService;
    private final String destinationQueueName;

    @Transactional
    public void submitJob(JobSubmissionRequest jobSubmissionRequest) {
        Job job = jobSubmissionRequest.getJob();

        enqueueMessage(
                job.getId(),
                resolveProducer(job.getConfig().getService()).produceJobSpec(jobSubmissionRequest),
                jobPriorityCalculator.calculateJobPriority(job)
        );
    }

    private JobSpecProducer resolveProducer(PlatformService service) {
        // The producers are held in a Set, whose iteration order is not fixed. Today each service type is supported by
        // exactly one producer, so returning the first match is unambiguous. If ever more than one producer supports
        // the same service type, this method would pick an arbitrary one - it would then need a deterministic selection.
        for (JobSpecProducer jobSpecProducer : jobSpecProducers) {
            if (jobSpecProducer.supports(service)) {
                return jobSpecProducer;
            }
        }
        throw new IllegalStateException("No JobSpecProducer supports service type " + service.getType());
    }

    private void enqueueMessage(Long jobId, JobSpec message, int priority) {
        HashMap<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("jobId", String.valueOf(jobId));
        queueService.sendObject(destinationQueueName, messageHeaders, message, priority);
        LOG.info("Sent message for job {} to queue {}", jobId, destinationQueueName);
    }
}