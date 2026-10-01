package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.repository.KubernetesWorkerJobRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Transactional implementation of the KubernetesWorkerJobDataService
 *
 */
@Slf4j
@Transactional(readOnly = true)
@AllArgsConstructor
public class KubernetesWorkerJobDataServiceImpl implements KubernetesWorkerJobDataService {

    private final KubernetesWorkerJobRepository kubernetesWorkerJobRepository;

    @Override
    @Transactional
    public KubernetesWorkerJob save(KubernetesWorkerJob kubernetesWorkerJob) {
        return kubernetesWorkerJobRepository.save(kubernetesWorkerJob);
    }

    @Override
    public Optional<KubernetesWorkerJob> findByJobId(String jobId) {
        return kubernetesWorkerJobRepository.findByJobId(jobId);
    }

    @Override
    public KubernetesWorkerJob findByIdOrDefault(KubernetesWorkerJob defaultKubernetesWorkerJob) {
        Optional<KubernetesWorkerJob> byJobId = findByJobId(defaultKubernetesWorkerJob.getJobId());

        if (byJobId.isPresent()) {
            return byJobId.get();
        }

        LOG.info("KubernetesWorkerJob entity for job id {} {} not found, returning the default value", defaultKubernetesWorkerJob.getJobId(), defaultKubernetesWorkerJob.getIntJobId());

        return defaultKubernetesWorkerJob;
    }

    @Override
    public List<KubernetesWorkerJob> findByStatusInAndJobType(List<KubernetesWorkerJob.Status> statuses, KubernetesWorkerJob.JobType jobType) {
        return kubernetesWorkerJobRepository.findByStatusInAndJobType(statuses, jobType);
    }
}
