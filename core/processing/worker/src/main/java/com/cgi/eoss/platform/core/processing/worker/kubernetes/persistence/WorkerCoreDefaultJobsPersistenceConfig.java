package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Provides the single-tenant persistence infrastructure for the Kubernetes worker jobs.
 * It is activated only when multi-tenancy is disabled, that is when the {@code platform.tenants.enabled} property is
 * set to {@code false} or is not defined at all.
 */
@Configuration
public class WorkerCoreDefaultJobsPersistenceConfig {
    /**
     * Provides the data source settings for the worker jobs database, bound to the
     * {@code platform.worker.jobs.datasource} configuration properties.
     *
     * @return the data source settings used to create the worker jobs {@link DataSource}
     */
    @Bean
    @ConfigurationProperties("platform.worker.jobs.datasource")
    public DataSourceProperties workerJobsDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Provides the data source for the worker jobs database, built from the {@link #workerJobsDataSourceProperties()}.
     *
     * @return the data source connecting to the worker jobs database
     */
    @Bean
    public DataSource workerJobsDataSource() {
        return workerJobsDataSourceProperties().initializeDataSourceBuilder().build();
    }

    /**
     * Resolves the effective Hibernate properties for the worker jobs persistence unit by merging the configured
     * Hibernate properties with the JPA properties.
     *
     * @param workerJobsHibernateProperties
     *            the Hibernate specific properties configured for the worker jobs persistence unit
     * @param workerJobsJpaProperties
     *            the JPA properties configured for the worker jobs persistence unit
     * @return the resolved Hibernate properties, keyed by property name, to be applied to the worker jobs entity
     *         manager
     */
    @Bean
    public Map<String, Object> workerJobsJpaPropertiesMap(
        HibernateProperties workerJobsHibernateProperties,
        JpaProperties workerJobsJpaProperties) {
        return workerJobsHibernateProperties.determineHibernateProperties(workerJobsJpaProperties.getProperties(), new HibernateSettings());
    }

    /**
     * Provides the strategy that applies the pending Flyway migrations to the worker jobs database. The migration is
     * triggered during bean initialization, so that the database schema is up to date before the entity manager is
     * created.
     *
     * @param flyway
     *            the configured Flyway instance pointing at the worker jobs database
     * @return the runnable that, when run, migrates the worker jobs database to the latest schema version
     */
    @Bean(initMethod = "run")
    public Runnable flywayMigrationStrategy(Flyway flyway) {
        return flyway::migrate;
    }


}
