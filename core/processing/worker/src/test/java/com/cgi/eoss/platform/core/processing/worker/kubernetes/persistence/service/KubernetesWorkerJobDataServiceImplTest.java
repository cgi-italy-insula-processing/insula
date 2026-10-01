package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.repository.KubernetesWorkerJobRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KubernetesWorkerJobDataServiceImplTest {

    private final KubernetesWorkerJobRepository kubernetesWorkerJobRepository = mock(KubernetesWorkerJobRepository.class);

    private final InOrder inOrder = inOrder(kubernetesWorkerJobRepository);

    private KubernetesWorkerJobDataServiceImpl kubernetesWorkerJobDataServiceImpl;

    @Before
    public void setUp() {
        kubernetesWorkerJobDataServiceImpl = new KubernetesWorkerJobDataServiceImpl(kubernetesWorkerJobRepository);
    }

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testFindByIdOrDefault_ReturnsKubernetesWorkerJobWithTheProvidedId() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("job-id");

        when(kubernetesWorkerJobRepository.findByJobId("job-id")).thenReturn(Optional.of(kubernetesWorkerJob));

        assertThat(kubernetesWorkerJobDataServiceImpl.findByIdOrDefault(kubernetesWorkerJob)).isEqualTo(kubernetesWorkerJob);

        inOrder.verify(kubernetesWorkerJobRepository).findByJobId("job-id");
    }

    @Test
    public void testFindByIdOrDefault_ReturnsDefaultValues_WhenJobWithTheProvidedIdDoesNotExist() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("nonexistent-job-id");

        when(kubernetesWorkerJobRepository.findByJobId("nonexistent-job-id")).thenReturn(Optional.empty());

        assertThat(kubernetesWorkerJobDataServiceImpl.findByIdOrDefault(kubernetesWorkerJob)).isEqualTo(kubernetesWorkerJob);

        inOrder.verify(kubernetesWorkerJobRepository).findByJobId("nonexistent-job-id");
    }

    @Test
    public void testFindByIdOrDefault_ThrowsRuntimeException_WhenDatabaseThrowsException() {

        KubernetesWorkerJob kubernetesWorkerJob = new KubernetesWorkerJob();
        kubernetesWorkerJob.setJobId("job-id");

        when(kubernetesWorkerJobRepository.findByJobId("job-id")).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> kubernetesWorkerJobDataServiceImpl.findByIdOrDefault(kubernetesWorkerJob))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database error");

        inOrder.verify(kubernetesWorkerJobRepository).findByJobId("job-id");
    }
}
