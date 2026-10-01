package com.cgi.eoss.platform.core.processing.server.persistence.service;

import static com.cgi.eoss.platform.core.processing.server.model.QJob .job;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.JobDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.google.common.collect.ArrayListMultimap;
import com.querydsl.core.types.Predicate;

@Service
@Transactional(readOnly = true)
public class JpaJobDataService extends AbstractJpaDataService<Job> implements JobDataService {

    private final JobDao dao;

    @Autowired
    public JpaJobDataService(JobDao jobDao) {
        this.dao = jobDao;
    }

    @Override
    protected PlatformEntityDao<Job> getDao() {
        return dao;
    }

    @Override
    protected Predicate getUniquePredicate(Job entity) {
        return job.extId.eq(entity.getExtId());
    }

    @Override
    public List<Job> findByOwner(User user) {
        return dao.findByOwner(user);
    }

    @Override
    public List<Job> findByService(PlatformService service) {
        return dao.findByConfig_Service(service);
    }

    @Override
    public List<Job> findByStatusAndGuiUrlNotNull(Status status) {
        return dao.findByStatusAndGuiUrlNotNull(status);
    }

    @Override
    public List<Job> findByStatusAndPhase(Status status, JobStep phase) {
        return dao.findByStatusAndPhase(status, phase);
    }

    @Override
    public List<Job> findByOwnerAndService(User user, PlatformService service) {
        return dao.findByOwnerAndConfig_Service(user, service);
    }

    @Override
    public Optional<Job> refreshFull(Long id) {
        Optional<Job> job = dao.findById(id);
        job.ifPresent(value -> value.getSubJobs().size());
        return job;
    }

    @Override
    public Job refreshFull(Job job) {
        job = refresh(job);
        job.getSubJobs().size();
        return job;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Job updateParentJobOutputs(Job job) {
        Job parentJob = this.refreshFull(job.getParentJob());
        if (parentJob.getOutputs() == null) {
            parentJob.setOutputs(ArrayListMultimap.create());
        }
        parentJob.getOutputs().putAll(job.getOutputs());
        return save(parentJob);
    }

    @Override
    @Transactional
    public Job save(Job job) {
        Job savedJob = super.save(job);
        updateParentJobStartDate(job);
        return savedJob;
    }

    @Override
    @Transactional
    public Collection<Job> save(Collection<Job> jobs) {
        jobs = super.save(jobs);
        jobs.forEach(this::updateParentJobStartDate);
        return jobs;
    }

    @Override
    public Integer countByOwnerAndStatusIn(User user, List<Status> status) {
        return dao.countByOwnerAndStatusIn(user, status);
    }

    @Override
    public Integer countByOwnerAndParentFalseAndStatusIn(User user, List<Status> status) {
        return dao.countByOwnerAndParentFalseAndStatusIn(user, status);
    }

    @Override
    public List<Job> findByOwnerAndParentFalseAndStartTimeBetween(User user, LocalDateTime startTime, LocalDateTime endTime) {
        return dao.findByOwnerAndParentFalseAndStartTimeBetween(user, startTime, endTime);
    }

    @Override
    public List<Job> findByExternalId(List<String> externalIds) {
        return dao.findByExtIdIn(externalIds);
    }

    @Override
    public List<Job> findByIds(List<Long> ids) {
        return dao.findByIdInOrderByIdAsc(ids);
    }

    @Override
    public List<Long> getSubJobIds(Job parentJob) {
        return dao.findAllIdsByParentJobId(parentJob);
    }

    @Override
    public long countSubJobs(Job parentJob) {
        return dao.countByParentJob(parentJob);
    }

    @Override
    public List<Long> getSubJobIdsWithStatus(Job parentJob, Status status) {
        return dao.findAllIdsByParentJobIdAndStatus(parentJob, status);
    }

    @Override
    public Map<Status, Long> countSubJobStatuses(Job parentJob) {
        if (!parentJob.isParent()) {
            return null;
        }

        Map<Status, Long> countSubJobStatuses = new EnumMap<>(Status.class);
        for (Object[] row : dao.countSubJobStatuses(parentJob)) {
            countSubJobStatuses.put((Status) row[0], ((Number) row[1]).longValue());
        }

        return countSubJobStatuses;
    }

    @Override
    public boolean allSubJobsCompleted(Job parentJob) {
        Map<Status, Long> subJobStatuses = countSubJobStatuses(parentJob);
        return subJobStatuses != null && subJobStatuses.size() == 1
                && subJobStatuses.getOrDefault(Status.COMPLETED, 0L) > 0L;
    }

    @Override
    public List<Job> findByParentJobAndStatusAndPhaseIn(Job parentJob, Status status, List<JobStep> phases, Integer limit) {
        return dao.findByParentJobAndStatusAndPhaseIn(parentJob, status, phases,
            limit != null ? Pageable.ofSize(limit) : Pageable.unpaged()).getContent();
    }

    @Override
    public Page<Job> findByParentJobAndStatusAndPhaseInPaged(Job parentJob, Status status, List<JobStep> phases, Pageable pageable) {
        return dao.findByParentJobAndStatusAndPhaseIn(parentJob, status, phases, pageable);
    }

    @Override
    public Integer countByParentJobAndStatusAndPhaseIn(Job parentJob, Status status, List<JobStep> phases) {
        return dao.countByParentJobAndStatusAndPhaseIn(parentJob, status, phases);
    }

    private Job updateParentJobStartDate(Job childJob) {
        if (childJob.getParentJob() == null || childJob.getStartTime() == null) {
            return childJob.getParentJob();
        }

        Job parentJob = refreshFull(childJob.getParentJob());
        if (parentJob.getStartTime() != null) {
            return parentJob;
        }
        parentJob.setStartTime(childJob.getStartTime());
        return save(parentJob);
    }
}
