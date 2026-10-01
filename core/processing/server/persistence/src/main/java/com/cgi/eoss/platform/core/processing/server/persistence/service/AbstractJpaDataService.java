package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformEntity;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.querydsl.core.types.Predicate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
public abstract class AbstractJpaDataService<T extends PlatformEntity<T>> implements PlatformEntityDataService<T> {
    @Override
    @Transactional
    public void delete(T entity) {
        getDao().delete(entity);
    }

    @Override
    @Transactional
    public void deleteAll() {
        getDao().deleteAll();
    }

    @Override
    public List<T> getAll() {
        return getDao().findAll();
    }

    @Override
    public List<T> getAllFull() {
        return getDao().findAll();
    }

    @Override
    @Transactional
    public T save(T entity) {
        resyncId(entity);
        return getDao().saveAndFlush(entity);
    }

    @Override
    @Transactional
    public Collection<T> save(Collection<T> entities) {
        entities.forEach(this::resyncId);
        return getDao().saveAllAndFlush(entities);
    }

    @Override
    public Optional<T> getById(Long id) {
        return getDao().findById(id);
    }

    @Override
    public List<T> getByIds(Collection<Long> ids) {
        return getDao().findAllById(ids);
    }

    @Override
    public boolean isUnique(T entity) {
        return !getDao().exists(getUniquePredicate(entity));
    }

    @Override
    public boolean isUniqueAndValid(T obj) {
        return isUnique(obj);
    }

    @Override
    public Optional<T> findOneByExample(T example) {
        return getDao().findOne(getUniquePredicate(example));
    }

    @Override
    public T refresh(T obj) {
        return getDao().findOne(getUniquePredicate(obj)).orElseThrow(() -> new EmptyResultDataAccessException(1));
    }

    @Override
    public T refreshFull(T obj) {
        return refresh(obj);
    }

    @Override
    public T convert(Long source) {
        return getById(source).orElse(null);
    }

    /**
     * @param entity The potentially detached entity
     */
    private void resyncId(T entity) {
        Predicate predicate = getUniquePredicate(entity);
        if (predicate != null) {
            Optional<T> tmp = getDao().findOne(predicate);
            tmp.ifPresent(t -> entity.setId(t.getId()));
        };
    }

    protected abstract PlatformEntityDao<T> getDao();

    protected abstract Predicate getUniquePredicate(T entity);

}
