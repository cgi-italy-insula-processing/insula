package com.cgi.eoss.platform.core.processing.server.persistence.service;

import static com.cgi.eoss.platform.core.processing.server.model.QUser.user;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserDao;
import com.querydsl.core.types.Predicate;

@Service
@Transactional(readOnly = true)
public class JpaUserDataService extends AbstractJpaDataService<User> implements UserDataService {

    private final UserDao dao;

    private final ProcessingCoreDataInitializationService processingCoreDataInitializationService;

    @Autowired
    public JpaUserDataService(UserDao userDao, ProcessingCoreDataInitializationService processingCoreDataInitializationService) {
        this.dao = userDao;
        this.processingCoreDataInitializationService = processingCoreDataInitializationService;
    }

    @Override
    protected PlatformEntityDao<User> getDao() {
        return dao;
    }

    @Override
    protected Predicate getUniquePredicate(User entity) {
        return user.name.eq(entity.getName());
    }

    @Override
    public List<User> search(String term) {
        return dao.findByNameContainingIgnoreCase(term);
    }

    @Override
    public User getByName(String name) {
        return maybeGetByName(name).orElse(null);
    }

    @Transactional
    @Override
    public User getOrSave(String name) {
        return maybeGetByName(name).orElseGet(() -> save(new User(name)));
    }

    @Override
    public User getDefaultUser() {
        return processingCoreDataInitializationService.getDefaultUser();
    }

    @Override
    public String getDefaultUserName() {
        return processingCoreDataInitializationService.getDefaultUserName();
    }

    @Override
    public User getDefaultAdmin() {
        return processingCoreDataInitializationService.getDefaultAdmin();
    }

    @Override
    public User refreshFull(User userToRefresh) {
        userToRefresh = refresh(userToRefresh);
        return userToRefresh;
    }

    private Optional<User> maybeGetByName(String name) {
        return Optional.ofNullable(dao.findOneByName(name));
    }

}
