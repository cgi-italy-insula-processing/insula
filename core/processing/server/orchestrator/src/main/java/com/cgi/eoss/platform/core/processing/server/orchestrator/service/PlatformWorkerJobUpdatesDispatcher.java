package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import java.io.Serializable;
import java.util.Optional;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.rpc.worker.JobError;
import com.cgi.eoss.platform.rpc.worker.JobEvent;
import com.cgi.eoss.platform.rpc.worker.JobEventType;

@AllArgsConstructor
@Slf4j
public class PlatformWorkerJobUpdatesDispatcher {

    private final JobDataService jobDataService;
    private final WorkerJobUpdatesManager workerJobUpdatesManager;

    /**
     * JMS listener that dispatches job update messages to the business layer.
     * <p>
     * It receives job update events from the job updates queue
     * and dispatches them to the appropriate handler
     * based on the update type.
     * </p>
     *
     * @param objectMessage the JMS message containing the job update
     * @param workerId the identifier of the worker VM where the job is executed. Unused on Kubernetes deployments
     * @param internalJobId the internal identifier of the job being updated
     */
    @JmsListener(destination = ProcessingCoreQueueNames.JOB_UPDATES)
    public void receiveJobUpdateMessage(
            @Payload ObjectMessage objectMessage,
            @Header("workerId") String workerId,
            @Header("jobId") String internalJobId) {
        try {
            Serializable update = objectMessage.getObject();
            String messageType = getMessageType(update);
            LOG.info("Job update '{}' received: jobId '{}' - redelivered '{}'",
                    messageType,
                    internalJobId,
                    objectMessage.getBooleanProperty(JmsHeaders.REDELIVERED));

            dispatchJobUpdate(update, workerId, internalJobId);

            LOG.info("Job update '{}' completed for jobId '{}'", messageType, internalJobId);
        } catch (JMSException e) {
            LOG.error("Failed getting the serializable job '{}' message update: {}", internalJobId, e.getMessage());
            Optional<Job> job = jobDataService.refreshFull(Long.parseLong(internalJobId));
            if (job.isPresent()) {
                workerJobUpdatesManager.onJobError(job.get(), e);
            } else {
                LOG.error("Ignoring Job updates for job with ID {}, failed to load job ", internalJobId);
            }
        } catch (PlatformEntityNotFoundException e) {
            LOG.error("Job update failed for job '{}': failed to load job from DB", internalJobId, e);
        } catch (Exception e) {
            LOG.error("Job update failed for job '{}'", internalJobId, e);
            throw e;
        }

    }

    public void dispatchJobUpdate(Object update, String workerId, String internalJobId) {
        Job job = jobDataService.refreshFull(Long.parseLong(internalJobId))
                .orElseThrow(() -> new PlatformEntityNotFoundException("Failed to load job with ID: " + internalJobId));
        if (update instanceof JobEvent) {
            JobEvent jobEvent = (JobEvent) update;
            JobEventType jobEventType = jobEvent.getJobEventType();
            if (jobEventType == JobEventType.DATA_FETCHING_STARTED) {
                workerJobUpdatesManager.onJobDataFetchingStarted(job, workerId);
            } else if (jobEventType == JobEventType.DATA_FETCHING_COMPLETED) {
                workerJobUpdatesManager.onJobDataFetchingCompleted(job);
            } else if (jobEventType == JobEventType.PROCESSING_STARTED) {
                workerJobUpdatesManager.onJobProcessingStarted(job, jobEvent.getTimestamp(),
                        PlatformService.Type.APPLICATION.equals(job.getConfig().getService().getType()) ? jobEvent.getJobEventPayload() : workerId);
            } else if (jobEventType == JobEventType.PROCESSING_COMPLETED) {
                workerJobUpdatesManager.onJobProcessingCompleted(job, jobEvent.getTimestamp());
            } else if (jobEventType == JobEventType.HEARTBEAT) {
                workerJobUpdatesManager.onJobHeartbeat(job, jobEvent.getTimestamp());
            }
        } else if (update instanceof JobError) {
            JobError jobError = (JobError) update;
            workerJobUpdatesManager.onJobError(job, jobError.getErrorDescription());
        }
    }

    private static String getMessageType(Serializable message) {
        if (message instanceof JobEvent) {
            return ((JobEvent) message).getJobEventType().toString();
        } else {
            return message.getClass().getCanonicalName();
        }
    }
}
