package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

/**
 * Custom repository API for managing {@link PlatformService} resources.
 */

public interface ServicesApiCustom extends BaseRepositoryApi<PlatformService> {

    /**
     * Finds platform services based on the provided statuses and pageable information.
     *
     * @param statuses the collection of platform service statuses to filter by
     * @param pageable the pageable information for pagination
     * @return a page of platform services matching the specified criteria
     */
    Page<PlatformService> parametricFind(Collection<PlatformService.Status> statuses, Pageable pageable);

}
