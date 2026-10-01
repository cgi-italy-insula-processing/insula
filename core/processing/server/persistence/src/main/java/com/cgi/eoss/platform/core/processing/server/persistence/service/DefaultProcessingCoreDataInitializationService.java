package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.Role;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible to initialize and retrieve the default entities and to expose their properties
 */
@Log4j2
@RequiredArgsConstructor
public class DefaultProcessingCoreDataInitializationService implements ProcessingCoreDataInitializationService {

    private final String defaultUserName = "default-user";
    private final String defaultUserMail = "default-user@test.dev";
    private final Role defaultUserRole = Role.USER;

    private final UserDao userDao;

    /**
     * Ensures the default entities exists, creating them if needed
     */
    @Override
    @Transactional
    public void ensureDefaultEntitiesExist() {
        if (getDefaultUser() != null) {
            return;
        }
        createDefaultUser();
    }

    /**
     * Retrieves the default user entity if exists
     * @return the default user entity
     */
    @Override
    @Transactional(readOnly = true)
    public User getDefaultUser() {
        return userDao.findOneByName(defaultUserName);
    }

    /**
     * Returns the default user userName
     * @return the default user userName
     */
    @Override
    public String getDefaultUserName() {
        return "default-user";
    }

    /**
     * Returns the default user entity delegating the retrieval to {@link com.cgi.eoss.platform.core.processing.server.persistence.service.DefaultProcessingCoreDataInitializationService#getDefaultUser()}
     * @return the default user entity
     */
    @Override
    @Transactional(readOnly = true)
    public User getDefaultAdmin() {
        return getDefaultUser();
    }

    /**
     * Get the platform docker image prefix
     * @return the platform docker image prefix
     */
    @Override
    public String getPlatformDockerPrefix() {
        return "";
    }

    private void createDefaultUser() {

        LOG.info("Creating default user '{}'", defaultUserName);
        User defaultUser = new User(defaultUserName);
        defaultUser.setEmail(defaultUserMail);
        defaultUser.setRole(defaultUserRole);

        userDao.save(defaultUser);
    }
}
