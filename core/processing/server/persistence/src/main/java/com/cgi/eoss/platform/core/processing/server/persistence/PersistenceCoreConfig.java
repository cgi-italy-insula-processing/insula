package com.cgi.eoss.platform.core.processing.server.persistence;

import com.cgi.eoss.platform.core.processing.server.model.PlatformEntity;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.UserDao;
import com.cgi.eoss.platform.core.processing.server.persistence.service.DefaultProcessingCoreDataInitializationService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ProcessingCoreDataInitializationManagedService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ProcessingCoreDataInitializationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * This class is responsible for the Spring configuration of the whole core-persistence module.
 */
@Configuration
@Import({
        PropertyPlaceholderAutoConfiguration.class,
        DataSourceConfig.class
})
@EnableJpaRepositories(basePackageClasses = PlatformEntityDao.class,
        excludeFilters = { @ComponentScan.Filter(SpringJpaRepositoryIgnore.class) })
@EntityScan(basePackageClasses = {PlatformEntity.class} )
@ComponentScan(basePackageClasses = PersistenceCoreConfig.class)
public class PersistenceCoreConfig {

    /**
     * Class defining the default configuration
     */
    @Configuration
    @ConditionalOnProperty(value="platform.server.app", havingValue = "processing-core")
    public static class DefaultConfig {

        /**
         * Initializes a {@link ProcessingCoreDataInitializationService} with the default configuration
         * @param userDao the {@link UserDao}
         * @return an initialized {@link ProcessingCoreDataInitializationService} bean
         */
        @Bean
        public ProcessingCoreDataInitializationService processingCoreDataInitializationService(UserDao userDao) {

            return new DefaultProcessingCoreDataInitializationService(userDao);
        }

        /**
         * Initializes the {@link ProcessingCoreDataInitializationManagedService} bean
         * responsible to ensure default entities are created when application is started
         * @param processingCoreDataInitializationService
         *   the {@link ProcessingCoreDataInitializationService} class providing method to create default entities
         * @return
         *   an initialized {@link ProcessingCoreDataInitializationManagedService}
         */
        @Bean
        public ProcessingCoreDataInitializationManagedService processingCoreDataInitializationManagedService(
                ProcessingCoreDataInitializationService processingCoreDataInitializationService) {

            return new ProcessingCoreDataInitializationManagedService(processingCoreDataInitializationService);
        }

    }

}
