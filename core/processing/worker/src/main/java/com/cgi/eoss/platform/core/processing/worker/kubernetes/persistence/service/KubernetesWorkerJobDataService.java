package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;

import java.util.List;
import java.util.Optional;

/**
 * Interface that defines the methods to save and find the KubernetesWorkerJob entity
 */
public interface KubernetesWorkerJobDataService {

    /**
     * Save the KubernetesWorkerJob entity
     *
     * @param kubernetesWorkerJob entity to save
     * @return entity saved
     */
    KubernetesWorkerJob save(KubernetesWorkerJob kubernetesWorkerJob);

    /**
     * Retrieve the KubernetesWorkerJob having the provided id
     *
     * @param jobId The id of the KubernetesWorkerJob to retrieve
     * @return An optional wrapping the KubernetesWorkerJob having the provided id if present, or an empty optional otherwise
     */
    Optional<KubernetesWorkerJob> findByJobId(String jobId);

    /**
     * Retrieve the KubernetesWorkerJob having the provided KubernetesWorkerJob or return the provided default KubernetesWorkerJob if not found
     *
     * @param defaultKubernetesWorkerJob The default KubernetesWorkerJob to return if the KubernetesWorkerJob having the provided id is not found
     * @return The KubernetesWorkerJob having the provided id if found, or the provided default KubernetesWorkerJob otherwise
     */
    KubernetesWorkerJob findByIdOrDefault(KubernetesWorkerJob defaultKubernetesWorkerJob);

    /**
     * Retrieves KubernetesWorkerJobs with Status in a list of possible {@link KubernetesWorkerJob.Status} and a JobType
     * @param statuses A list containing the possible statuses of the KubernetesWorkerJobs to retrieve
     * @param jobType The jobType of the KubernetesWorkerJobs to retrieve
     * @return A List containing the retrieved KubernetesWorkerJobs
     */
    List<KubernetesWorkerJob> findByStatusInAndJobType(List<KubernetesWorkerJob.Status> statuses, KubernetesWorkerJob.JobType jobType);
}
