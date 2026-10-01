package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.testutils;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.repository.KubernetesWorkerJobRepository;
import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

@AllArgsConstructor
@Transactional(readOnly = true)
public class PersistenceTestUtils {

    private final KubernetesWorkerJobRepository kubernetesWorkerJobRepository;

    @Transactional
    public void cleanDatabase() {
        kubernetesWorkerJobRepository.deleteAll();
    }

    public List<KubernetesWorkerJob> findAllKubernetesWorkerJob() {
        return kubernetesWorkerJobRepository.findAll();
    }

    public void assertDbIsEmpty() {
        assertThat(findAllKubernetesWorkerJob()).isEmpty();
    }

}
