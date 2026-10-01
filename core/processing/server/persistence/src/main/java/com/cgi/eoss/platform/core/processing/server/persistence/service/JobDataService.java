package com.cgi.eoss.platform.core.processing.server.persistence.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;

public interface JobDataService extends
        PlatformEntityDataService<Job> {

    List<Job> findByOwner(User user);

    List<Job> findByService(PlatformService service);

    List<Job> findByOwnerAndService(User user, PlatformService service);

    List<Job> findByOwnerAndParentFalseAndStartTimeBetween(User user, LocalDateTime startDateTime,
            LocalDateTime endDateTime);

    List<Job> findByStatusAndGuiUrlNotNull(Status status);

    List<Job> findByStatusAndPhase(Status status, JobStep phase);

    Job updateParentJobOutputs(Job job);

    Optional<Job> refreshFull(Long id);

    Integer countByOwnerAndStatusIn(User user, List<Status> status);

    /**
     * Counts the {@link Job} entities of the given owner that are not parent jobs and whose status is
     * within the provided list.
     *
     * @param user   the owner of the jobs;
     * @param status the list of statuses to match;
     * @return the number of non-parent jobs of the given owner having one of the provided statuses
     */
    Integer countByOwnerAndParentFalseAndStatusIn(User user, List<Status> status);

    /**
     * Retrieves a list of {@link Job} entities that have an external ID matching any of the provided values.
     *
     * @param externalIds a list of external IDs to search for; must not be {@code null} or empty
     * @return a list of {@link Job} entities with matching external IDs; never {@code null},
     *      but may be empty if no jobs are found for the given search criteria
     */
    List<Job> findByExternalId(List<String> externalIds);

    /**
     * Retrieves a list of {@link Job} entities that have an ID matching any of the provided values.
     *
     * @param ids a list of IDs to search for; must not be {@code null} or empty
     * @return an ordered list of {@link Job} entities with matching IDs; never {@code null},
     *      but may be empty if no sub-jobs are found for the given search criteria
     */
    List<Job> findByIds(List<Long> ids);

    /** Retrieves a set of IDs of {@link Job} entities that have the specified parent job.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return an ordered list of IDs of {@link Job} entities that are children of the specified {@code parentJob};
     *         never {@code null}, but may be empty if no matching jobs are found
     */
    List<Long> getSubJobIds(Job parentJob);

    /**
     * Counts {@link Job} entities having the specified parent job.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return the number of child {@link Job} entities associated with the specified {@code parentJob};
     *         returns {@code 0} if no matching jobs are found
     */
    long countSubJobs(Job parentJob);

    /** Retrieves a set of IDs of {@link Job} entities that have the specified parent job and status.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status the status used as the search criterion; must not be {@code null}
     * @return an ordered list of IDs of {@link Job} entities that are children of the specified {@code parentJob} and have the specified {@code status};
     *         never {@code null}, but may be empty if no matching jobs are found
     */
    List<Long> getSubJobIdsWithStatus(Job parentJob, Status status);


    /** Retrieves a list of status counts for sub-jobs of a given parent job.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return an {@link java.util.Map} that maps each {@link Job.Status} present in the data to the corresponding
     *         count of sub-jobs; {@code null} when the provided job is not marked as a parent job; an empty map when
     *         the provided parent job has no sub-jobs
     */
    Map<Status, Long> countSubJobStatuses(Job parentJob);

    /** Determines whether all subJobs of a given parent job have reached a completed status.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return true if all sub-jobs of the specified {@code parentJob} have a status that is considered completed
     */
    boolean allSubJobsCompleted(Job parentJob);

    /**
     * Retrieves a list of {@link Job} entities that are children of the specified {@code parentJob}, have the
     * given {@code status} and whose {@code phase} is one of the provided phases.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status the {@link Status} used as the search criterion; must not be {@code null}
     * @param phases to include in the result; must not be {@code null}
     * @param limit maximum number of results to return; if {@code null}, all matching jobs will be returned
     * @return a list of {@link Job} entities matching the criteria; never {@code null}, but may be empty
     */
    List<Job> findByParentJobAndStatusAndPhaseIn(Job parentJob, Status status, List<JobStep> phases, Integer limit);

    /**
     * Retrieves Page of {@link Job} entities that are children of the specified {@code parentJob}, have the
     * given {@code status} and whose {@code phase} is one of the provided phases.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status the {@link Status} used as the search criterion; must not be {@code null}
     * @param phases to include in the result; must not be {@code null}
     * @param pageable the pagination information; must not be {@code null}
     * @return the requested page of {@link Job} entities matching the criteria; never {@code null}, but may be empty
     */
    Page<Job> findByParentJobAndStatusAndPhaseInPaged(Job parentJob, Status status, List<JobStep> phases, Pageable pageable);

    /**
     * Counts {@link Job}s that are children of the specified {@code parentJob}, have the
     * given {@code status} and whose {@code phase} is one of the provided phases.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status the {@link Status} used as the search criterion; must not be {@code null}
     * @param phases to include from in the result; must not be {@code null}
     * @return the number of {@link Job} entities matching the criteria; returns {@code 0} if no matches are found
     */
    Integer countByParentJobAndStatusAndPhaseIn(Job parentJob, Status status, List<JobStep> phases);

}
