package com.cgi.eoss.platform.core.processing.server.persistence.service;

import lombok.AllArgsConstructor;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Service responsible to initialize default entities once the Spring Application has started
 *
 */
@AllArgsConstructor
public class ProcessingCoreDataInitializationManagedService {

    private final ProcessingCoreDataInitializationService processingCoreDataInitializationService;

    /**
     * Listens for the spring ContextRefreshedEvent and checks whether default
     * entities exist and, if this is not the case, creates them
     *
     */
    @EventListener(ContextRefreshedEvent.class)
    public void ensureDefaultEntitiesExist() {
        processingCoreDataInitializationService.ensureDefaultEntitiesExist();
    }

}
