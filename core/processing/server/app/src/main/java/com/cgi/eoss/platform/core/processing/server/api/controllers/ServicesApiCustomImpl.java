package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.QPlatformService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformServiceValidator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.CwlService;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformServiceDao;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JpaServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.google.common.base.Strings;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.net.URI;
import java.util.Collection;

/**
 * Implementation of the {@link ServicesApiCustom} interface providing custom repository operations for {@link PlatformService} resources.
 */

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Getter
@Component
@Log4j2
public class ServicesApiCustomImpl extends BaseRepositoryApiImpl<PlatformService> implements ServicesApiCustom {

    private final PlatformServiceDao dao;

    private final JpaServiceDataService jpaServiceDataService;

    private final UserDataService userDataService;

    private final ServiceDataService serviceDataService;
    private final PlatformServiceValidator serviceValidator;
    private final CwlService cwlService;

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Saves the given platform service, applying the processing-core persistence rules.
     *
     * <p>On update, checks whether the change is allowed. If the service is defined through CWL,
     * the entity is populated from the CWL document. Sets the default user as owner when none is
     * set, lowercases the docker tag (prefixing it only when a platform docker prefix is configured),
     * and validates the entity before persisting it.</p>
     *
     * @param entity the platform service to save
     * @param <S>    the concrete {@link PlatformService} type
     * @return the persisted platform service
     * @throws IllegalArgumentException if the update is not allowed or the entity fails validation
     */
    @Override
    public <S extends PlatformService> S save(S entity) {

        Long entityId = entity.getId();
        if (entityId != null) {
            entityManager.detach(entity);
            serviceDataService.getById(entityId)
                    .ifPresent(service -> checkIfUpdateIsAllowed(service, entity));
        }
        S entityToBeSaved = entity;

        if (isFromCwl(entityToBeSaved)) {
            entityToBeSaved = getEntityFromCwl(entityToBeSaved.getCwl().getUrl(), entityId);
        }

        if (entityToBeSaved.getOwner() == null) {
            entityToBeSaved.setOwner(userDataService.getDefaultUser());
        }

        setLowercaseDockerTag(entityToBeSaved, serviceDataService.getPlatformDockerPrefix());

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(entityToBeSaved);
        assertIsValid(validationResult);

        return getDao().save(entityToBeSaved);
    }

    @Override
    public Page<PlatformService> parametricFind(Collection<PlatformService.Status> statuses, Pageable pageable) {

        return getFilteredResults(buildPredicate(statuses), pageable);
    }

    private Predicate buildPredicate(Collection<PlatformService.Status> statuses) {

        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (statuses != null && !statuses.isEmpty()) {
            booleanBuilder.and(QPlatformService.platformService.status.in(statuses));
        }

        return booleanBuilder.getValue();
    }

    private <S extends PlatformService> void checkIfUpdateIsAllowed(PlatformService service, S entity) {
        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(service, entity);
        assertIsValid(validationResult);
    }

    private void assertIsValid(PlatformServiceValidator.PlatformServiceValidationResult validationResult) {
        if (!validationResult.isValid()) {
            LOG.error(validationResult.getErrorMessage());
            throw new IllegalArgumentException(validationResult.getErrorMessage());
        }
    }

    private static <S extends PlatformService> void setLowercaseDockerTag(S entity, String dockerPrefix) {
        if (!Strings.isNullOrEmpty(dockerPrefix)) {
            String prefixWithSlash = dockerPrefix + "/";
            if (!entity.getDockerTag().startsWith(prefixWithSlash)) {
                entity.setDockerTag(prefixWithSlash + entity.getDockerTag());
            }
        }
        entity.setDockerTag(entity.getDockerTag().toLowerCase());
    }

    private <S extends PlatformService> S getEntityFromCwl(URI cwlUrl, Long entityId) {
        S entity = (S) cwlService.populateFromCwl(cwlUrl);
        entity.setId(entityId);
        return entity;
    }

    private static boolean isFromCwl(PlatformService entity) {
        return entity.getCwl() != null;
    }
}