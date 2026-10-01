package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserMountDao;
import com.querydsl.core.types.Predicate;

import static com.cgi.eoss.platform.core.processing.server.model.QUserMount.userMount;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class JpaUserMountDataService extends AbstractJpaDataService<UserMount>
        implements UserMountDataService {

    private final UserMountDao dao;

    @Autowired
    public JpaUserMountDataService(UserMountDao userMountDao) {
        this.dao = userMountDao;
    }
    
    @Override
    protected PlatformEntityDao<UserMount> getDao() {
        return dao;
    }

    @Override
    protected Predicate getUniquePredicate(UserMount entity) {
        return userMount.name.eq(entity.getName())
                .and(userMount.owner.eq(entity.getOwner()));
    }

    @Override
    public Optional<UserMount> getByName(String name) {
        return dao.findByName(name);
    }
}
