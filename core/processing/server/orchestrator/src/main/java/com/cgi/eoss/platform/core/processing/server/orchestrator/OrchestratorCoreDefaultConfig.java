package com.cgi.eoss.platform.core.processing.server.orchestrator;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.CoreJobPriorityCalculator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.CoreOutputProcessor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.CoreProcessorJobSpecProducer;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.CoreWorkerJobUpdatesManager;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.DefaultJobOutputsRepatriationServiceProperties;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.DefaultOutputProductProvisioner;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputLocator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputRepatriationCoreService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputsRepatriationService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputsRetrievalService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.OutputProcessor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.OutputProductProvisioner;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobLauncherCore;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobManager;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobSubmitter;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobStopRequestSubmitter;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobStopper;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.UserMountResolver;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.queues.QueuesCoreDefaultConfig;
import com.cgi.eoss.platform.core.queues.service.QueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputsProcessor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputsValidator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobValidationResultProducer;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.StacJobInputsProcessor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacAssetRelocator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacInputsService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacItemDeserializer;

import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;

/**
 * Core configuration holding the beans activated when {@code platform.server.app} has value {@code processing-core}.
 */
@Configuration
@Import({QueuesCoreDefaultConfig.class})
@ConditionalOnProperty(name = "platform.server.app", havingValue = "processing-core", matchIfMissing = false)
public class OrchestratorCoreDefaultConfig {

    /**
     * The STAC-only job inputs processor.
     *
     * @param stacInputsService the service processing STAC-typed job inputs
     * @return the STAC-only job inputs processor
     */
    @Bean
    public JobInputsProcessor jobInputsProcessor(StacInputsService stacInputsService) {
        return new StacJobInputsProcessor(stacInputsService);
    }

    /**
     * The {@link DefaultJobOutputsRepatriationServiceProperties} used to configure the job outputs repatriation service.
     *
     * @param jobOutputsBucketName the name of the bucket where job outputs are stored
     * @return the {@link DefaultJobOutputsRepatriationServiceProperties}
     */
    @Bean
    public DefaultJobOutputsRepatriationServiceProperties jobOutputsRepatriationServiceProperties(
        @Value("${platform.orchestrator.objectStorage.jobOutputsBucketName:job-outputs}") String jobOutputsBucketName) {
        return DefaultJobOutputsRepatriationServiceProperties.builder()
            .jobOutputsBucketName(jobOutputsBucketName)
            .build();
    }

    /**
     * Creates the default output product provisioner that provisions the output products
     * under the same base directory.
     *
     * @param baseDir the output products base directory.
     * @return an {@link OutputProductProvisioner} instance provisioning under {@code baseDir}.
     */
    @Bean
    public DefaultOutputProductProvisioner outputProductProvisioner(
        @Value("${platform.orchestrator.outputProducts.baseDir:/data/outputProducts}") Path baseDir) {
        return new DefaultOutputProductProvisioner(baseDir.toAbsolutePath());
    }

    /**
     * Creates the bean {@link JobOutputRepatriationCoreService}
     *
     * @param outputProductProvisioner the provisioner used to resolve the output product storage location
     * @return the {@link JobOutputRepatriationCoreService}
     */
    @Bean
    public JobOutputRepatriationCoreService jobOutputRepatriationService(OutputProductProvisioner outputProductProvisioner) {
        return new JobOutputRepatriationCoreService(outputProductProvisioner);
    }

    /**
     * Creates the bean {@link CoreOutputProcessor}
     *
     * @param jobOutputsRepatriationService the service that repatriates the job outputs
     * @param jobDataService the service that handles the job data
     * @param baseDir the job output products base directory
     * @return the {@link CoreOutputProcessor}
     */
    @Bean
    public CoreOutputProcessor outputProcessor(JobOutputsRepatriationService jobOutputsRepatriationService,
                                               JobDataService jobDataService,
                                               @Value("${platform.orchestrator.outputProducts.baseDir:/data/outputProducts}") Path baseDir) {
        return new CoreOutputProcessor(jobOutputsRepatriationService, jobDataService, baseDir.toAbsolutePath());
    }

    /**
     * The core worker job updates manager that handles the job updates from the workers.
     *
     * @param jobDataService the service that handles the job data
     * @param outputProcessor the processor that handles the job outputs
     * @return the {@link CoreWorkerJobUpdatesManager}
     */
    @Bean
    public CoreWorkerJobUpdatesManager workerJobUpdatesManager(JobDataService jobDataService, OutputProcessor outputProcessor){
        return new CoreWorkerJobUpdatesManager(jobDataService, outputProcessor);
    }

    /**
     * The core processor job spec producer that produces the job specs for the processors.
     *
     * @param jobDataService the service that handles the job data
     * @param userMountResolver the resolver that resolves the user mounts
     * @return the {@link CoreProcessorJobSpecProducer}
     */
    @Bean
    public CoreProcessorJobSpecProducer processorJobSpecProducer(JobDataService jobDataService, UserMountResolver userMountResolver) {
        return new CoreProcessorJobSpecProducer(jobDataService, userMountResolver);
    }

    /**
     * The ordered validators, holding only the optional job inputs validator.
     *
     * @param jobInputsValidator the optional job inputs validator
     * @return the ordered validators
     */
    @Bean
    public List<JobValidationResultProducer> orderedValidators(Optional<JobInputsValidator> jobInputsValidator) {
        ImmutableList.Builder<JobValidationResultProducer> validators = ImmutableList.builder();
        jobInputsValidator.ifPresent(validators::add);
        return validators.build();
    }

    /**
     * Creates the {@link PlatformJobStopper} bean providing methods able to interrupt jobs' executions
     * @param jobDataService the bean providing method to interact with Job entities
     * @param platformJobStopRequestSubmitter the bean used to submit jobs' stop requests
     * @param queueService the bean used to send or receive messages from queues
     * @param subJobsPageSize the size used to retrieve jobs according to pagination
     * @param pendingJobsQueueName the name of the pending jobs queue
     * @param waitingJobsQueueName the name of the waiting jobs queue
     * @return an initialized PlatformJobStopper bean
     */
    @Bean
    public PlatformJobStopper platformJobStopper(JobDataService jobDataService,
                                                 PlatformJobStopRequestSubmitter platformJobStopRequestSubmitter,
                                                 QueueService queueService,
                                                 @Value("${platform.orchestrator.job.stopper.subJobsPageSize:100}")
                                                     Integer subJobsPageSize,
                                                 @Value("${platform.orchestrator.job.stopper.pendingQueueName:platform-pending-jobs}")
                                                     String pendingJobsQueueName,
                                                 @Value("${platform.orchestrator.job.stopper.waitingQueueName:platform-jobs}")
                                                     String waitingJobsQueueName) {
        return new PlatformJobStopper(jobDataService, platformJobStopRequestSubmitter, queueService,
                subJobsPageSize, pendingJobsQueueName, waitingJobsQueueName);
    }

    /**
     * Creates the {@link JobOutputLocator} that resolves the folders and files of the job outputs stored under
     * the output products base directory.
     *
     * @param baseDir the base directory under which the job outputs are stored
     * @return the {@link JobOutputLocator}
     */
    @Bean
    public JobOutputLocator jobOutputLocator(
        @Value("${platform.orchestrator.outputProducts.baseDir:/data/outputProducts}") Path baseDir) {
        return new JobOutputLocator(baseDir.toAbsolutePath().normalize());
    }

    /**
     * Creates the {@link StacItemDeserializer} that reads the STAC Items produced by the jobs and synthesizes
     * default STAC Items for the other job output files.
     *
     * @param objectMapper the object mapper used to deserialize the STAC Items
     * @return the {@link StacItemDeserializer}
     */
    @Bean
    public StacItemDeserializer stacItemDeserializer(ObjectMapper objectMapper) {
        return new StacItemDeserializer(objectMapper);
    }

    /**
     * Creates the {@link JobOutputsRetrievalService} that serves the job output files and describes them
     * as STAC Collections.
     *
     * @param jobDataService the data service used to load the jobs and their sub-jobs
     * @param jobOutputLocator the locator resolving the folders and files of the job outputs
     * @param stacItemDeserializer the deserializer providing the STAC Items describing the job outputs
     * @param baseUrl the base URL of the API under which the job output files can be downloaded, without
     *                trailing slash
     * @return the {@link JobOutputsRetrievalService}
     */
    @Bean
    public JobOutputsRetrievalService jobOutputsRetrievalService(JobDataService jobDataService,
                                                                 JobOutputLocator jobOutputLocator,
                                                                 StacItemDeserializer stacItemDeserializer,
                                                                 @Value("${platform.orchestrator.jobOutputs.baseUrl}") URL baseUrl) {
        return new JobOutputsRetrievalService(jobDataService, jobOutputLocator, stacItemDeserializer,
                new StacAssetRelocator(), baseUrl);
    }

    /**
     * Creates the {@link PlatformJobManager} bean providing methods able to launch jobs' executions
     * @param platformJobLauncherCore the bean providing methods to prepare job submission requests
     * @param platformJobSubmitter the bean used to submit jobs' execution requests
     * @return an initialized PlatformJobManager bean
     */
    @Bean
    public PlatformJobManager platformJobManager(PlatformJobLauncherCore platformJobLauncherCore,
                                                 PlatformJobSubmitter platformJobSubmitter) {
        return new PlatformJobManager(platformJobLauncherCore, platformJobSubmitter);
    }

    /**
     * Creates the no-op CoreJobPriorityCalculator bean
     *
     * @return a no-op CoreJobPriorityCalculator bean
     */
    @Bean
    public CoreJobPriorityCalculator jobPriorityCalculator() {
        return new CoreJobPriorityCalculator();
    }
}
