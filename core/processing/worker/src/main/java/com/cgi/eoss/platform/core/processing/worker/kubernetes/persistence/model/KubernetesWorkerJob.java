package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Defines the KubernetesWorkerJob entity
 */
@Data
@Entity
@Table(name = "kubernetes_worker_jobs")
@NoArgsConstructor
@EqualsAndHashCode(of = "jobId")
public class KubernetesWorkerJob {

    private static final Long DEFAULT_GROUP_ID = 0L;

    @Id
    @Size(min = 1)
    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Size(min = 1)
    @Column(name = "int_job_id", nullable = false)
    private String intJobId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private KubernetesWorkerJob.Status status = KubernetesWorkerJob.Status.STARTING;

    @Column(name = "start_time")
    private OffsetDateTime start;

    @Column(name = "end_time")
    private OffsetDateTime end;

    @Column(name = "group_id")
    private Long groupId = DEFAULT_GROUP_ID;

    @Column(name = "job_type")
    @Enumerated(EnumType.STRING)
    private KubernetesWorkerJob.JobType jobType;

    public KubernetesWorkerJob(String jobId, String intJobId) {
        this.jobId = jobId;
        this.intJobId = intJobId;
    }

    public enum Status {
        STARTING,
        STARTED,
        COMPLETED,
        FAILED,
        ERROR,
        NOT_AVAILABLE,
        STOPPED
    }

    /**
     * Determines whether this job is allowed to transition to the {@link Status#STARTED} status
     * based on its current status.
     *
     * @return true if the current status is neither {@link Status#STARTING} nor {@link Status#STARTED}, false otherwise
     */
    public boolean isStartedStatusTransitionAllowed() {

        Status myStatus = getStatus();
        return !(myStatus == Status.STARTING ||
                 myStatus == Status.STARTED);
    }

    /**
     * Determines whether this job is allowed to transition to the {@link Status#STOPPED} status
     * based on its current status.
     *
     * @return true if the current status is not a terminal one ({@link Status#COMPLETED}, {@link Status#FAILED},
     * {@link Status#ERROR} or {@link Status#STOPPED}), false otherwise
     */
    public boolean isStoppedStatusTransitionAllowed() {

        Status myStatus = getStatus();
        return !(myStatus == Status.COMPLETED ||
                 myStatus == Status.FAILED ||
                 myStatus == Status.ERROR ||
                 myStatus == Status.STOPPED);
    }

    public enum JobType {
        WORKFLOW,
        INTERACTIVE_APPLICATION
    }

}
