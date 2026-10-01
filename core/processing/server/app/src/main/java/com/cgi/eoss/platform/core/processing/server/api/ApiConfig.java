package com.cgi.eoss.platform.core.processing.server.api;

import com.cgi.eoss.platform.core.processing.server.api.mappers.JobOutputFilesMapper;
import com.cgi.eoss.platform.core.processing.server.api.projections.DetailedJob;
import com.cgi.eoss.platform.core.processing.server.api.projections.DetailedPlatformService;
import com.cgi.eoss.platform.core.processing.server.api.projections.ShortJob;
import com.cgi.eoss.platform.core.processing.server.api.projections.ShortPlatformService;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;

import static org.springframework.data.rest.core.mapping.RepositoryDetectionStrategy.RepositoryDetectionStrategies.ANNOTATED;


/**
 * Spring configuration class for the API layer of the platform.
 */
@Configuration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class})
@Import({
    OrchestratorCoreConfig.class
})
@ComponentScan( basePackageClasses = {ApiConfig.class})
@EnableJpaRepositories(basePackageClasses = ApiConfig.class)
public class ApiConfig implements WebMvcConfigurer, RepositoryRestConfigurer {

    @Value("${platform.api.basePath:/api}")
    private String apiBasePath;

    /**
     * Creates a guavaModule Bean that will be registered to the ObjectMapper used in
     * serialization/deserialization for both Spring DATA REST and Spring MVC rest controllers.
     *
     * @return {@link GuavaModule} for Jackson serialization/deserialization.
     */
    @Bean
    public GuavaModule guavaModule() {
        return new GuavaModule();
    }


    /**
     * Creates a javaTimeModule Bean that will be registered to the ObjectMapper used in
     * serialization/deserialization for both Spring DATA REST and Spring MVC rest controllers.
     *
     * @return {@link JavaTimeModule} for Jackson serialization/deserialization.
     */
    @Bean
    public JavaTimeModule javaTimeModule() {
        return new JavaTimeModule();
    }

    /**
     * Creates a hibernate5Module Bean that will be registered to the ObjectMapper used in
     * serialization/deserialization for both Spring DATA REST and Spring MVC rest controllers.
     *
     * @return {@link Hibernate5Module} for Jackson serialization/deserialization.
     */
    @Bean
    public Hibernate5Module hibernate5Module() {
        return new Hibernate5Module();
    }


    /**
     * Prepends the {@link #apiBasePath} prefix to the endpoints in Spring Web MVC classes
     * annotated with {@link RestController} annotation.
     *
     * @param configurer {@link PathMatchConfigurer} Bean created by Spring Web MVC.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(apiBasePath, HandlerTypePredicate.forAnnotation(RestController.class));
    }

    /**
     * Customize the format of responses returned by  {@link RestController}s when client
     * doesn't specify the preferred ones
     *
     * @param configurer {@link ContentNegotiationConfigurer} Bean created by Spring Web MVC.
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaTypes.HAL_JSON);
    }

    /**
     * Customizes the CorsConfiguration and CorsConfigurationSource Bean
     * when platform's security CORS property is enabled. This default configuration allows
     * all origins, headers and methods to access the API endpoints.
     *
     * @return {@link CorsConfigurationSource} Bean containing a customized CORS configuration.
     */
    @Bean
    @ConditionalOnProperty(value = "platform.api.security.cors.enabled", havingValue = "true", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowedMethods(ImmutableList.of(HttpMethod.GET.name(), HttpMethod.POST.name(),
            HttpMethod.HEAD.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name(),
            HttpMethod.PATCH.name(), HttpMethod.PUT.name(), HttpMethod.TRACE.name()));
        source.registerCorsConfiguration(apiBasePath + "/**", config);
        return source;
    }

    /**
     * Customizes the CorsConfiguration and CorsConfigurationSource Bean
     * when platform's security CORS property is not enabled. This default configuration doesn't
     * set specific checks on API requests and should not be used.
     *
     * @return {@link CorsConfigurationSource} Bean containing a customized CORS configuration.
     */
    @Bean
    @ConditionalOnProperty(value = "platform.api.security.cors.enabled", havingValue = "false")
    public CorsConfigurationSource disabledCorsConfiguration() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        source.registerCorsConfiguration(apiBasePath + "/**", config);
        return source;
    }

    /**
     * Creates a {@link WebSecurityConfigurerAdapter} Bean that disables Spring Security
     * for all API endpoints. This is required to allow anonymous access to the API endpoints
     * when Spring Security is enabled in the platform.
     *
     * @return {@link WebSecurityConfigurerAdapter} Bean that disables Spring Security for all API endpoints.
     */
    @Bean
    public WebSecurityConfigurerAdapter webSecurityConfigurerAdapter() {
        return new WebSecurityConfigurerAdapter() {
            @Override
            protected void configure(HttpSecurity httpSecurity) throws Exception {
                httpSecurity
                    .authorizeRequests()
                    .anyRequest().anonymous();
                httpSecurity
                    .csrf().disable();
                httpSecurity
                    .cors();
                httpSecurity
                    .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            }
        };
    }

    /**
     * Creates the {@link ProjectionFactory} bean used by Spring Data REST
     * to support SpEL expressions in projections
     *
     * @return The {@link ProjectionFactory} bean
     */
    @Bean
    public ProjectionFactory projectionFactory() {
        return new SpelAwareProxyProjectionFactory();
    }

    /**
     * Creates the mapper exposing the output files of a job with their download links, rooted at the base path
     * of the API.
     *
     * @param jobDataService the data service managing the Job entities
     * @return the {@link JobOutputFilesMapper} bean
     */
    @Bean
    public JobOutputFilesMapper jobOutputFilesMapper(JobDataService jobDataService) {
        return new JobOutputFilesMapper(jobDataService, apiBasePath);
    }


    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
        config.setRepositoryDetectionStrategy(ANNOTATED);
        config.setBasePath(apiBasePath);
        config.setDefaultMediaType(MediaTypes.HAL_JSON);
        config.getProjectionConfiguration().addProjection(DetailedJob.class)
                .addProjection(ShortJob.class).addProjection(ShortPlatformService.class)
                .addProjection(DetailedPlatformService.class);

        // Ensure that the id attribute is returned for all API-mapped types
        ImmutableList
            .of(JobConfig.class, Job.class, PlatformService.class)
            .forEach(config::exposeIdsFor);
    }
}
