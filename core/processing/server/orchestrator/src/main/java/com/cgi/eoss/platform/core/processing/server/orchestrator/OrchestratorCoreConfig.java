package com.cgi.eoss.platform.core.processing.server.orchestrator;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputsProcessor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputsValidator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputRepatriationService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputsRepatriationService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputsRepatriationServiceProperties;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobPriorityCalculator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobSpecProducer;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobValidationResultProducer;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobValidator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobLauncherCore;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobStopRequestSubmitter;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobSubmitter;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformServiceValidator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformWorkerJobUpdatesDispatcher;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.UserMountResolver;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.WorkerJobUpdatesManager;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.CwlService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.CwlServiceProperties;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.JobResourceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.JobResourceManagementService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacInputsServiceCoreConfiguration;
import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserMountDataService;
import com.cgi.eoss.platform.core.queues.QueuesCoreBaseConfig;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for the core Orchestrator component.
 * Provides the resource-management beans shared by the platform orchestrator, leaving the
 * deployment-specific beans to the importing configuration.
 */
@Configuration
@EnableConfigurationProperties(CwlServiceProperties.class)
@Import({
        JacksonAutoConfiguration.class,
        OrchestratorCoreDefaultConfig.class,
        PropertyPlaceholderAutoConfiguration.class,
        JobResourceCoreConfig.class,
        PersistenceCoreConfig.class,
        StacInputsServiceCoreConfiguration.class,
        QueuesCoreBaseConfig.class
})
@Slf4j
public class OrchestratorCoreConfig {

    @Bean
    public OkHttpClient cwlHttpClient(CwlServiceProperties cwlServiceProperties) {
        return new OkHttpClient().newBuilder()
                .connectTimeout(cwlServiceProperties.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(cwlServiceProperties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(cwlServiceProperties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .callTimeout(cwlServiceProperties.getCallTimeoutSeconds(), TimeUnit.SECONDS)
                .addInterceptor(new HttpLoggingInterceptor(LOG::trace).setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();
    }

    /**
     * The {@link CwlService} used to populate a {@link com.cgi.eoss.platform.core.processing.server.model.PlatformService}
     * from a CWL application package.
     *
     * @param cwlHttpClient        the HTTP client used to fetch the CWL document
     * @param cwlServiceProperties the CWL service configuration properties
     * @param userMountDataService the data service used to resolve the service's user mounts
     * @return the {@link CwlService}
     */
    @Bean
    public CwlService cwlService(OkHttpClient cwlHttpClient,
                                 CwlServiceProperties cwlServiceProperties,
                                 UserMountDataService userMountDataService) {
        return new CwlService(cwlHttpClient, cwlServiceProperties, userMountDataService);
    }

    /**
     * The {@link PlatformWorkerJobUpdatesDispatcher} that receives job update messages from the job updates queue
     * and dispatches them to the appropriate handler based on the update type.
     *
     * @param jobDataService          the data service used to persist job updates
     * @param workerJobUpdatesManager the manager that handles job updates and drives a job through its life cycle
     * @return the {@link PlatformWorkerJobUpdatesDispatcher}
     */
    @Bean
    public PlatformWorkerJobUpdatesDispatcher platformWorkerJobUpdatesDispatcher(JobDataService jobDataService, WorkerJobUpdatesManager workerJobUpdatesManager) {
        return new PlatformWorkerJobUpdatesDispatcher(jobDataService, workerJobUpdatesManager);
    }

    /**
     * The object-storage client used to store and retrieve job artifacts.
     *
     * @param retries           the maximum number of retries on error
     * @param connectionTimeout the connection timeout, in milliseconds
     * @param socketTimeout     the socket timeout, in milliseconds
     * @param protocol          the object-storage protocol (HTTP or HTTPS)
     * @param accessKey         the object-storage access key
     * @param secretKey         the object-storage secret key
     * @param endpoint          the object-storage endpoint
     * @param region            the object-storage region
     * @return the object-storage client
     */
    @Bean
    public AmazonS3 amazonS3(
            @Value("${platform.orchestrator.objectStorage.retries:3}") int retries,
            @Value("${platform.orchestrator.objectStorage.connectionTimeout:10000}") int connectionTimeout,
            @Value("${platform.orchestrator.objectStorage.socketTimeout:10000}") int socketTimeout,
            @Value("${platform.orchestrator.objectStorage.protocol:HTTP}") String protocol,
            @Value("${platform.orchestrator.objectStorage.accessKey:access}") String accessKey,
            @Value("${platform.orchestrator.objectStorage.secretKey:secret}") String secretKey,
            @Value("${platform.orchestrator.objectStorage.endpoint:endpoint}") String endpoint,
            @Value("${platform.orchestrator.objectStorage.region:region}") String region) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");
        clientConfiguration.setMaxErrorRetry(retries);
        clientConfiguration.setConnectionTimeout(connectionTimeout);
        clientConfiguration.setSocketTimeout(socketTimeout);
        clientConfiguration.setProtocol(protocol.equals("HTTP") ? Protocol.HTTP : Protocol.HTTPS);
        return AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(accessKey, secretKey)))
                .build();
    }

    /**
     * Creates the bean {@link JobOutputsRepatriationService}
     *
     * @param jobOutputsRepatriationServiceProperties the properties used to configure the repatriation service
     * @param amazonS3                                the Amazon S3 client used to access the object storage
     * @param jobOutputRepatriationService            the core service that repatriates a single job output
     * @return the {@link JobOutputsRepatriationService}
     */
    @Bean
    public JobOutputsRepatriationService jobOutputsRepatriationService(JobOutputsRepatriationServiceProperties jobOutputsRepatriationServiceProperties,
                                                                       AmazonS3 amazonS3, JobOutputRepatriationService jobOutputRepatriationService) {
        return new JobOutputsRepatriationService(jobOutputsRepatriationServiceProperties, amazonS3, jobOutputRepatriationService);
    }

    /**
     * The {@link UserMountResolver} that resolves a service's additional mounts into the gRPC user mount representations.
     *
     * @param userMountDataService the data service used to load the user mounts referenced by the service
     * @return the {@link UserMountResolver}
     */
    @Bean
    public UserMountResolver userMountResolver(UserMountDataService userMountDataService) {
        return new UserMountResolver(userMountDataService);
    }

    /**
     * The {@link PlatformJobSubmitter} that submits jobs to the platform job queue.
     *
     * @param jobSpecProducers      the set of job spec producers used to produce the job spec for each job
     * @param jobPriorityCalculator the bean responsible for calculating priority for jobs
     * @param queueService          the queue service used to submit the job spec to the platform job queue
     * @param destinationQueueName  the name of the platform job queue
     * @return the {@link PlatformJobSubmitter}
     */
    @Bean
    public PlatformJobSubmitter platformJobSubmitter(
            Set<JobSpecProducer> jobSpecProducers,
            JobPriorityCalculator jobPriorityCalculator,
            QueueService queueService,
            @Value("${platform.orchestrator.jobSubmitter.queue.name:platform-pending-jobs}") String destinationQueueName) {
        return new PlatformJobSubmitter(
                jobSpecProducers,
                jobPriorityCalculator,
                queueService,
                destinationQueueName);
    }

    /**
     * The job inputs validator checking a job's inputs against its service descriptor.
     *
     * @param alwaysAllowedValues the input ids validated only as well-formed JSON; empty unless configured
     * @return the job inputs validator
     */
    @Bean
    @ConditionalOnProperty(value = "platform.orchestrator.job-inputs-validator.enabled", havingValue = "true")
    public JobInputsValidator jobInputsValidator(
            @Value("${platform.orchestrator.job-inputs-validator.always-allowed-values:}") String[] alwaysAllowedValues) {
        return new JobInputsValidator(Arrays.asList(alwaysAllowedValues));
    }

    /**
     * The job validator running the ordered validators against a job.
     *
     * @param validators the ordered validators to run
     * @return the job validator
     */
    @Bean
    public JobValidator jobValidator(@Qualifier("orderedValidators") List<JobValidationResultProducer> validators) {
        return new JobValidator(validators);
    }

    /**
     * Instantiate the platform service validator used to validate platform services before they are persisted.
     *
     * @return the {@link PlatformServiceValidator}
     */
    @Bean
    public PlatformServiceValidator platformServiceValidator() {
        return new PlatformServiceValidator();
    }

/**
 * The processing core for job submission: loads the target service, validates and persists the job(s) and their
 * inputs, evaluates the resource requirements and returns the ready-to-submit requests.
 *
 * @param serviceDataService           the data service used to load the target service
 * @param userDataService              the data service used to resolve the job owner
 * @param jobInputsProcessor           the processor used to explode and resolve the job inputs
 * @param jobDataService               the data service used to persist the jobs
 * @param jobConfigDataService         the data service used to persist the job configurations
 * @param jobValidator                 the validator run against each job
 * @param jobResourceManagementService the service used to evaluate the job resource requirements
 * @return the {@link PlatformJobLauncherCore}
 */
@Bean
public PlatformJobLauncherCore platformJobLauncherCore(ServiceDataService serviceDataService, UserDataService userDataService,
                                                       JobInputsProcessor jobInputsProcessor, JobDataService jobDataService,
                                                       JobConfigDataService jobConfigDataService, JobValidator jobValidator,
                                                       JobResourceManagementService jobResourceManagementService) {
    return new PlatformJobLauncherCore(serviceDataService, userDataService, jobInputsProcessor, jobDataService,
            jobConfigDataService, jobValidator, jobResourceManagementService);
}

    /**
     * Creates a {@link PlatformJobStopRequestSubmitter} bean
     * @param queueService the service able to send and receive messages to/from queues
     * @return an initialized PlatformJobStopRequestSubmitter bean
     */
    @Bean
    public PlatformJobStopRequestSubmitter platformJobStopRequestSubmitter(QueueService queueService) {

        return new PlatformJobStopRequestSubmitter(queueService);
    }

}