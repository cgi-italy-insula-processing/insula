package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Custom repository API for managing {@link Job} resources.
 */
public interface JobsApiCustom extends BaseRepositoryApi<Job> {

    /**
     * Finds jobs based on the provided statuses and pageable information.
     *
     * @param statuses the collection of job statuses to filter by
     * @param pageable the pageable information for pagination
     * @return a page of jobs matching the specified criteria
     */
    Page<Job> parametricFind(Collection<Status> statuses,
                             LocalDateTime startDateTime,
                             LocalDateTime endDateTime,
                             Pageable pageable);
}