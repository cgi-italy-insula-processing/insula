package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import java.util.HashMap;
import java.util.Map;

import com.cgi.eoss.platform.core.queues.service.ProcessingCoreQueueNames;
import lombok.AllArgsConstructor;

import com.cgi.eoss.platform.core.queues.service.QueueService;

@AllArgsConstructor
public class PlatformWorkerUpdateManager implements JobUpdateListener {

    private final QueueService queueService;
    private final String workerId;


    @Override
    public void jobUpdate(String jobIntId, Object update) {
        Map<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("workerId", workerId);
        messageHeaders.put("jobId", jobIntId);
        queueService.sendObject(ProcessingCoreQueueNames.JOB_UPDATES, messageHeaders, update);
    }
}
