package com.cgi.eoss.platform.core.processing.server.persistence.dao;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface JobDao extends PlatformEntityDao<Job> {
    List<Job> findByOwner(User user);

    List<Job> findByConfig_Service(PlatformService service);

    List<Job> findByStatusAndGuiUrlNotNull(Status status);

    List<Job> findByStatusAndPhase(Status status, JobStep phase);

    List<Job> findByOwnerAndConfig_Service(User user, PlatformService service);

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

    List<Job> findByOwnerAndParentFalseAndStartTimeBetween(User user, LocalDateTime startTime,
                                                           LocalDateTime endTime);

    /**
     * Retrieves a list of {@link Job} entities that have an external ID matching any of the provided values.
     *
     * @param externalIds a list of external IDs to search for; must not be {@code null} or empty
     * @return a list of {@link Job} entities with matching external IDs; never {@code null}, but may be empty if no jobs are found for the given search criteria
     */
    List<Job> findByExtIdIn(List<String> externalIds);

    /**
     * Retrieves a list of {@link Job} entities that have an ID matching any of the provided values.
     *
     * @param ids a list of IDs to search for; must not be {@code null} or empty
     * @return a list of {@link Job} entities with matching IDs; never {@code null}, but may be empty if no matches are found
     */
    List<Job> findByIdInOrderByIdAsc(List<Long> ids);

    /**
     * Retrieves a set of IDs of {@link Job} entities that have the specified parent job.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return a set of IDs of {@link Job} entities that are children of the specified {@code parentJob};
     * never {@code null}, but may be empty if no matching jobs are found
     */
    @Query("SELECT j.id FROM Job j WHERE j.parentJob = :parentJob order by j.id ASC")
    List<Long> findAllIdsByParentJobId(@Param("parentJob") Job parentJob);

    /**
     * Counts {@link Job} entities having the specified parent job.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return the number of child {@link Job} entities associated with the specified {@code parentJob};
     * returns {@code 0} if no matching jobs are found
     */
    @Query("SELECT COUNT(j) FROM Job j WHERE j.parentJob = :parentJob")
    long countByParentJob(@Param("parentJob") Job parentJob);

    /**
     * Retrieves a set of IDs of {@link Job} entities that have the specified parent job and status.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status    the status used as the search criterion; must not be {@code null}
     * @return a set of IDs of {@link Job} entities that are children of the specified {@code parentJob} and have the specified {@code status};
     * never {@code null}, but may be empty if no matching jobs are found
     */
    @Query("SELECT j.id FROM Job j WHERE j.parentJob = :parentJob AND j.status = :status order by j.id ASC")
    List<Long> findAllIdsByParentJobIdAndStatus(@Param("parentJob") Job parentJob, @Param("status") Status status);

    /**
     * Retrieves a Page of {@link Job} entities that are children of the specified {@code parentJob}, have the
     * given {@code status} and whose {@code phase} is one of the provided phases.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status the {@link Status} used as the search criterion; must not be {@code null}
     * @param phases to include from in the result; must not be {@code null}
     * @param pageable the pagination information; must not be {@code null}
     * @return {@Page Page} of {@link Job} entities matching the criteria; never {@code null}, but may be empty
     */
    @Query("SELECT j FROM Job j WHERE j.parentJob = :parentJob AND j.status = :status AND j.phase IN :phases order by j.id ASC")
    Page<Job> findByParentJobAndStatusAndPhaseIn(@Param("parentJob") Job parentJob,
                                                 @Param("status") Status status,
                                                 @Param("phases") List<JobStep> phases,
                                                 Pageable pageable);

    /**
     * Counts {@link Job}s that are children of the specified {@code parentJob}, have the
     * given {@code status} and whose {@code phase} is one of the provided phases.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @param status the {@link Status} used as the search criterion; must not be {@code null}
     * @param phases to include from in the result; must not be {@code null}
     * @return the number of {@link Job} entities matching the criteria; returns {@code 0} if no matches are found
     */
    @Query("SELECT count(1) FROM Job j WHERE j.parentJob = :parentJob AND j.status = :status AND j.phase IN :phases")
    Integer countByParentJobAndStatusAndPhaseIn(@Param("parentJob") Job parentJob,
                                                 @Param("status") Status status,
                                                 @Param("phases") List<JobStep> phases);

    /** Retrieves a list of status counts for sub-jobs of a given parent job.
     *
     * @param parentJob the parent {@link Job} used as the search criterion; must not be {@code null}
     * @return a list of object arrays, where each array contains two elements: the first element is the status of the
     * sub-jobs, and the second element is the count of sub-jobs with that status;
     * never {@code null}, but may be empty if no sub-jobs are found for the given parent job
     */
    @Query("SELECT j.status AS status, COUNT(j) AS cnt " +
           "FROM Job j " +
           "WHERE j.parentJob = :parentJob " +
           "GROUP BY j.status")
    List<Object[]> countSubJobStatuses(@Param("parentJob") Job parentJob);

}
