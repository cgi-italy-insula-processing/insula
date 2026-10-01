package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob.Status;
import org.junit.Test;

public class KubernetesWorkerJobTest {

    @Test
    public void testIsStartedTransitionAllowed_ReturnsFalse_WhenJobStatusIsStartingStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.STARTING).isStartedStatusTransitionAllowed()).isFalse();
    }

    @Test
    public void testIsStartedTransitionAllowed_ReturnsFalse_WhenJobStatusIsStartedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.STARTED).isStartedStatusTransitionAllowed()).isFalse();
    }

    @Test
    public void testIsStartedTransitionAllowed_ReturnsTrue_WhenJobStatusIsCompletedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.COMPLETED).isStartedStatusTransitionAllowed()).isTrue();
    }

    @Test
    public void testIsStartedTransitionAllowed_ReturnsTrue_WhenJobStatusIsFailedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.FAILED).isStartedStatusTransitionAllowed()).isTrue();
    }

    @Test
    public void testIsStartedTransitionAllowed_ReturnsTrue_WhenJobStatusIsErrorStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.ERROR).isStartedStatusTransitionAllowed()).isTrue();
    }

    @Test
    public void testIsStartedTransitionAllowed_ReturnsTrue_WhenJobStatusIsStoppedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.STOPPED).isStartedStatusTransitionAllowed()).isTrue();
    }

    @Test
    public void testIsStoppedTransitionAllowed_ReturnsTrue_WhenJobStatusIsStartingStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.STARTING).isStoppedStatusTransitionAllowed()).isTrue();
    }

    @Test
    public void testIsStoppedTransitionAllowed_ReturnsTrue_WhenJobStatusIsStartedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.STARTED).isStoppedStatusTransitionAllowed()).isTrue();
    }

    @Test
    public void testIsStoppedTransitionAllowed_ReturnsFalse_WhenJobStatusIsCompletedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.COMPLETED).isStoppedStatusTransitionAllowed()).isFalse();
    }

    @Test
    public void testIsStoppedTransitionAllowed_ReturnsFalse_WhenJobStatusIsFailedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.FAILED).isStoppedStatusTransitionAllowed()).isFalse();
    }

    @Test
    public void testIsStoppedTransitionAllowed_ReturnsFalse_WhenJobStatusIsErrorStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.ERROR).isStoppedStatusTransitionAllowed()).isFalse();
    }

    @Test
    public void testIsStoppedTransitionAllowed_ReturnsFalse_WhenJobStatusIsStoppedStatus() {
        assertThat(createKubernetesWorkerJobWithStatus(Status.STOPPED).isStoppedStatusTransitionAllowed()).isFalse();
    }

    private KubernetesWorkerJob createKubernetesWorkerJobWithStatus(Status status) {
        KubernetesWorkerJob workerJob = new KubernetesWorkerJob("jobId", "intJobId");
        workerJob.setStatus(status);
        return workerJob;
    }

}
