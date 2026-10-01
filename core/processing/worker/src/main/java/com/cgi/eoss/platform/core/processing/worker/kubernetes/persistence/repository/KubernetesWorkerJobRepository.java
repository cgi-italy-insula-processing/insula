package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.repository;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * DAO interface for KubernetesWorkerJob entity objects.
 */

public interface KubernetesWorkerJobRepository extends JpaRepository<KubernetesWorkerJob, String> {

    /**
     * Retrieve the KubernetesWorkerJob having the provided id
     *
     * @param jobId The id of the KubernetesWorkerJob to retrieve
     * @return An optional wrapping the KubernetesWorkerJob having the provided id if present within the repository, or an empty optional otherwise
     */
    Optional<KubernetesWorkerJob> findByJobId(String jobId);

    /**
     * Retrieves the {@link KubernetesWorkerJob}s having the provided status and jobType
     * @param status the KubernetesWorkerJob status
     * @param jobType the KubernetesWorkerJob jobType
     * @return A list containing the KubernetesWorkerJobs with the provided status and jobType
     */
    List<KubernetesWorkerJob> findByStatusInAndJobType(List<KubernetesWorkerJob.Status> status, KubernetesWorkerJob.JobType jobType);

}
