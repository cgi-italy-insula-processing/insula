package com.cgi.eoss.platform.core.processing.server.persistence.dao;

import com.cgi.eoss.platform.core.processing.server.model.PlatformEntity;
import com.cgi.eoss.platform.core.processing.server.persistence.SpringJpaRepositoryIgnore;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;

/**
 * <p>A generic DAO interface for Platform entity objects.</p>
 *
 * @param <T> The type of entity managed by this DAO. It is expected that the type has a Long-type primary identifier.
 */
@SpringJpaRepositoryIgnore
public interface PlatformEntityDao<T extends PlatformEntity<T>> extends JpaRepository<T, Long>, QuerydslPredicateExecutor<T> {

    @Override
    List<T> findAll(Predicate predicate);

    @Override
    List<T> findAll(Predicate predicate, Sort sort);

    @Override
    List<T> findAll(Predicate predicate, OrderSpecifier<?>... orders);

    @Override
    List<T> findAll(OrderSpecifier<?>... orders);

    @Query("select distinct t.id from #{#entityName} t")
    List<Long> findAllIds();

}
