package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.QJob;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.JobDao;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Implementation of the {@link JobsApiCustom} interface providing custom repository operations for {@link Job} resources.
 */
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Getter
@Component
public class JobsApiCustomImpl extends BaseRepositoryApiImpl<Job> implements JobsApiCustom {

    private final JobDao dao;

    BooleanExpression isNotParentJob() {
        return QJob.job.parent.isFalse();
    }

    @Override
    public Page<Job> parametricFind(Collection<Status> statuses,
                                    LocalDateTime startDateTime,
                                    LocalDateTime endDateTime,
                                    Pageable pageable) {
        Predicate p = buildPredicate(statuses, startDateTime, endDateTime);

        return getFilteredResults(p, pageable);
    }

    private Predicate buildPredicate(Collection<Status> statuses,
                                     LocalDateTime startDateTime,
                                     LocalDateTime endDateTime
                                     ) {

        BooleanBuilder builder = new BooleanBuilder();
        if (statuses != null && !statuses.isEmpty()) {
            builder.and(QJob.job.status.in(statuses));
            builder.and(isNotParentJob());
        }

        if (startDateTime != null && endDateTime != null) {
            builder.and(QJob.job.startTime.before(endDateTime)).and(QJob.job.endTime.after(startDateTime));
        }
        else if (startDateTime != null) {
            builder.and(QJob.job.startTime.before(startDateTime)).and(QJob.job.endTime.after(startDateTime));
        }
        else if (endDateTime != null){
            builder.and(QJob.job.startTime.before(endDateTime)).and(QJob.job.endTime.after(endDateTime));
        }

        return builder;
    }
}
