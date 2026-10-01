package com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.model.KubernetesWorkerJob;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.repository.KubernetesWorkerJobRepository;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.persistence.service.KubernetesWorkerJobDataServiceImpl;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Configuration of beans to handle persistence of worker core application for the deployment on kubernetes
 *
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = { KubernetesWorkerJobRepository.class },
        entityManagerFactoryRef = "workerJobsEntityManager",
        transactionManagerRef = "workerJobsTransactionManager")
@EnableTransactionManagement
@EnableConfigurationProperties({ JpaProperties.class, DataSourceProperties.class, FlywayProperties.class, HibernateProperties.class})
public class WorkerCoreJobsPersistenceConfig {

    @Bean
    public KubernetesWorkerJobDataServiceImpl kubernetesWorkerJobDataService(KubernetesWorkerJobRepository kubernetesWorkerJobRepository) {
        return new KubernetesWorkerJobDataServiceImpl(kubernetesWorkerJobRepository);
    }

    @Bean
    @ConfigurationProperties("platform.worker.jobs.jpa")
    public JpaProperties workerJobsJpaProperties() {
        return new JpaProperties();
    }

    @Bean
    @ConfigurationProperties("platform.worker.jobs.jpa.hibernate")
    public HibernateProperties workerJobsHibernateProperties() {
        return new HibernateProperties();
    }

    @Bean
    @DependsOn("flywayMigrationStrategy")
    public LocalContainerEntityManagerFactoryBean workerJobsEntityManager(DataSource workerJobsDataSource,
                                                                          @Qualifier("workerJobsJpaPropertiesMap") Map<String, Object> workerJobsJpaPropertiesMap) {
        AbstractJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();

        JpaProperties jpaProperties = workerJobsJpaProperties();
        jpaVendorAdapter.setShowSql(jpaProperties.isShowSql());
        jpaVendorAdapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        jpaVendorAdapter.setGenerateDdl(jpaProperties.isGenerateDdl());

        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();

        factoryBean.setJpaPropertyMap(workerJobsJpaPropertiesMap);
        factoryBean.setDataSource(workerJobsDataSource);
        factoryBean.setJpaVendorAdapter(jpaVendorAdapter);
        factoryBean.setPackagesToScan(KubernetesWorkerJob.class.getPackage().getName());

        return factoryBean;
    }

    @Bean
    @ConfigurationProperties("platform.worker.jobs.flyway")
    public FlywayProperties flywayProperties() {
        return new FlywayProperties();
    }

    @Bean
    public Flyway flyway(FlywayProperties flywayProperties, DataSource workerJobsDataSource) {
        return Flyway.configure()
                .locations(flywayProperties.getLocations().toArray(new String[0]))
                .dataSource(workerJobsDataSource)
                .table(flywayProperties.getTable())
                .load();
    }

    @Bean
    public PlatformTransactionManager workerJobsTransactionManager(DataSource workerJobsDataSource, @Qualifier("workerJobsJpaPropertiesMap") Map<String, Object> workerJobsJpaPropertiesMap) {
        return new JpaTransactionManager(workerJobsEntityManager(workerJobsDataSource, workerJobsJpaPropertiesMap).getObject());
    }

}
