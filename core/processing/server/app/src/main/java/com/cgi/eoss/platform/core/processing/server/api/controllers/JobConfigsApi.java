package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

/**
 * Repository REST API for managing {@link JobConfig} resources.
 */
@Primary
@RepositoryRestResource(path = "jobConfigs", itemResourceRel = "jobConfig", collectionResourceRel = "jobConfigs")
public interface JobConfigsApi extends JobConfigsApiCustom, PagingAndSortingRepository<JobConfig, Long> {

    @Override
    @RestResource(exported = false)
    Iterable<JobConfig> findAll(Sort sort);

    @Override
    @RestResource(exported = false)
    Page<JobConfig> findAll(Pageable pageable);

    @Override
    @RestResource(exported = false)
    <S extends JobConfig> Iterable<S> saveAll(Iterable<S> entities);

    @Override
    @RestResource(exported = false)
    boolean existsById(Long aLong);

    @Override
    @RestResource(exported = false)
    Iterable<JobConfig> findAll();

    @Override
    @RestResource(exported = false)
    Iterable<JobConfig> findAllById(Iterable<Long> longs);

    @Override
    @RestResource(exported = false)
    long count();

    @Override
    @RestResource(exported = false)
    void deleteById(Long aLong);

    @Override
    @RestResource(exported = false)
    void delete(JobConfig entity);

    @Override
    @RestResource(exported = false)
    void deleteAllById(Iterable<? extends Long> longs);

    @Override
    @RestResource(exported = false)
    void deleteAll(Iterable<? extends JobConfig> entities);

    @Override
    @RestResource(exported = false)
    void deleteAll();
}
