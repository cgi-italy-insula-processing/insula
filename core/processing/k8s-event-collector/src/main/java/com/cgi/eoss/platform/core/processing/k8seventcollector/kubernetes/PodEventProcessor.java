package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;


import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.CorePlatformLabels.PLATFORM_INT_JOB_ID_LABEL;
import static com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.CorePlatformLabels.PLATFORM_JOB_ID_LABEL;

/**
 * Processor of Kubernetes Pod events.
 * This implementation delegates the publishing of messages on a queue for relevant event.
 *
 * @author cantaveneraf
 *
 */
@Slf4j
@AllArgsConstructor
public class PodEventProcessor implements KubernetesEventProcessor<Response<V1Pod>> {

    private final List<PodEventHandler> podEventHandlers;

    @Override
    public void process(Response<V1Pod> event) {
        if (!isProcessable(event)){
            return;
        }
        handlePodEvent(event);
    }

    private void handlePodEvent(Response<V1Pod> event) {
        Optional<PodEventHandler> supportingPodEventHandler = podEventHandlers.stream()
                .filter(handler -> handler.supports(event))
                .findFirst();
        if(!supportingPodEventHandler.isPresent()){
            LOG.info("Ignoring unsupported pod event - Type : {} - Object : {}", event.type, event.object);
            return;
        }
        supportingPodEventHandler.get().handlePodEvent(event);
    }

    private static boolean isProcessable(Response<V1Pod> event) {
        if (event.type == null) {
            LOG.info("Ignoring pod event with null item type");
            return false;
        }
        if (event.object == null) {
            LOG.warn("Ignoring pod event with null item object (pod)");
            return false;
        }
        return hasRequiredJobIdentifiers(event.object);
    }

    private static boolean hasRequiredJobIdentifiers(V1Pod pod) {
        String jobId = pod.getMetadata().getLabels().get(PLATFORM_JOB_ID_LABEL);
        String intJobId = pod.getMetadata().getLabels().get(PLATFORM_INT_JOB_ID_LABEL);

        if (jobId == null || intJobId == null) {
            LOG.info("Ignoring pod event with null job identifiers: jobId '{}' intJobId: '{}'", jobId, intJobId);
            return false;
        }
        return true;
    }

}