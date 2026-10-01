package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.api.projections.ShortPlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Collection;

/**
 * Repository API for managing {@link PlatformService} resources.
 */
@Primary
@RepositoryRestResource(path = "services", itemResourceRel = "service", collectionResourceRel = "services", excerptProjection = ShortPlatformService.class)
public interface ServicesApi extends ServicesApiCustom, PagingAndSortingRepository<PlatformService, Long> {

    @Override
    @RestResource(path = "parametricFind", rel = "parametricFind")
    Page<PlatformService> parametricFind(@Param("status") Collection<PlatformService.Status> statuses, Pageable pageable);

    @Override
    @RestResource(exported = false)
    Iterable<PlatformService> findAll(Sort sort);

    @Override
    @RestResource(exported = false)
    Page<PlatformService> findAll(Pageable pageable);

    @Override
    @RestResource(exported = false)
    <S extends PlatformService> Iterable<S> saveAll(Iterable<S> entities);

    @Override
    @RestResource(exported = false)
    boolean existsById(Long aLong);

    @Override
    @RestResource(exported = false)
    Iterable<PlatformService> findAll();

    @Override
    @RestResource(exported = false)
    Iterable<PlatformService> findAllById(Iterable<Long> longs);

    @Override
    @RestResource(exported = false)
    long count();

    @Override
    @RestResource(exported = false)
    void deleteById(Long aLong);

    @Override
    @RestResource(exported = false)
    void delete(PlatformService entity);

    @Override
    @RestResource(exported = false)
    void deleteAllById(Iterable<? extends Long> longs);

    @Override
    @RestResource(exported = false)
    void deleteAll();

    @Override
    @RestResource(exported = false)
    void deleteAll(Iterable<? extends PlatformService> entities);

}
