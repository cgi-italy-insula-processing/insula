package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.utils.JobConfigBuilder;

import com.cgi.eoss.platform.core.processing.server.orchestrator.exceptions.JobLaunchException;
import com.cgi.eoss.platform.core.processing.server.orchestrator.exceptions.ServiceExecutionException;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobValidationResult;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.JobResourceManagementService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>
 * Processing core for job submission.
 * </p>
 * <p>
 * Loads the target service, validates and persists the job(s) and their inputs, evaluates the
 * resource requirements, and returns one {@link JobSubmissionRequest} per job to be submitted
 * (a single request for a single job, one per valid sub-job for a parallel job).
 * </p>
 */
@AllArgsConstructor
@Log4j2
public class PlatformJobLauncherCore {

    private static final String PARALLEL_INPUTS_LEGACY_KEY = "parallelInputs";

    private final ServiceDataService serviceDataService;
    private final UserDataService userDataService;
    private final JobInputsProcessor jobInputsProcessor;
    private final JobDataService jobDataService;
    private final JobConfigDataService jobConfigDataService;
    private final JobValidator jobValidator;
    private final JobResourceManagementService jobResourceManagementService;

    /**
     * Processes a job launch request into one or more ready-to-submit {@link JobSubmissionRequest}s:
     * a single request for a single job, or one request per sub-job for a parallel job.
     *
     * @param request the job launch request
     * @return one {@link JobSubmissionRequest} for a single job, or one per valid sub-job for a parallel job
     * @throws PlatformEntityNotFoundException if the service or the referenced parent job cannot be found
     * @throws JobLaunchException              if the target service is disabled
     * @throws ServiceExecutionException       if the job fails validation, or no valid sub-jobs exist for a
     *                                         parallel job
     */
    public List<JobSubmissionRequest> submitJob(JobLaunchRequest request) {
        PlatformService service = serviceDataService.getByName(request.getServiceId())
                .orElseThrow(() -> new PlatformEntityNotFoundException(
                        "Failed to load Service with ID: " + request.getServiceId()));

        if (PlatformService.Status.DISABLED.equals(service.getStatus())) {
            LOG.info("The service {} is in DISABLED status: no processing allowed", service.getId());
            throw new JobLaunchException("Service disabled");
        }

        return processJobLaunchRequest(request, service);
    }

    private List<JobSubmissionRequest> processJobLaunchRequest(JobLaunchRequest request, PlatformService service) {
        if (service.getType() == PlatformService.Type.PARALLEL_PROCESSOR) {
            return processJobLaunchRequestForParallelJobs(request, service);
        }
        return Collections.singletonList(processJobLaunchRequestForSingleJob(request, service));
    }

    private List<JobSubmissionRequest> processJobLaunchRequestForParallelJobs(JobLaunchRequest request, PlatformService service) {
        String jobId = request.getJobId();
        String parentId = request.getJobParent();

        Job parentJob;
        JobInputs jobInputs;
        if (Strings.isNullOrEmpty(parentId)) {
            JobConfig jobConfig = buildJobConfig(request, service, null);
            jobInputs = jobInputsProcessor.explodeInputs(new JobInputs(jobId, request.getUserId(), jobConfig));
            jobConfig.setInputs(jobInputs.getValuesMap());
            parentJob = buildParentJob(jobId, jobConfig);
            LOG.info("Created parent job {}", parentJob.getExtId());
        } else {
            LOG.info("Attaching sub-jobs to existing parent {}", parentId);
            parentJob = jobDataService.refreshFull(Long.valueOf(parentId))
                    .orElseThrow(() -> new PlatformEntityNotFoundException("Failed to load parent Job with ID: " + parentId));
            jobInputs = jobInputsProcessor.explodeInputs(new JobInputs(jobId, request.getUserId(), parentJob.getConfig()));
            parentJob.getConfig().setInputs(jobInputs.getValuesMap());
            String systematicParameter = parentJob.getConfig().getSystematicParameter();
            if (!Strings.isNullOrEmpty(systematicParameter)) {
                jobInputs = jobInputs.cloneWithNewInput(
                        getParallelInputsKey(parentJob),
                        systematicParameterValues(request, systematicParameter),
                        parentJob.getConfig());
            }
        }

        String parallelInputsKey = getParallelInputsKey(parentJob);
        JobInput parallelInput = jobInputsProcessor.splitAndExplodeInputs(jobInputs, parallelInputsKey);
        jobInputs.getInputs().replace(parallelInputsKey, parallelInput);

        parentJob = persistJob(parentJob);

        return buildSubJobSubmissionRequests(parentJob, jobInputs);
    }

    private JobSubmissionRequest processJobLaunchRequestForSingleJob(JobLaunchRequest request, PlatformService service) {
        String jobId = request.getJobId();
        Job parentJob = loadParentJobIfPresent(request.getJobParent());
        JobConfig jobConfig = buildJobConfig(request, service, parentJob);

        JobInputs explodedInputs = jobInputsProcessor.explodeInputs(new JobInputs(jobId, request.getUserId(), jobConfig));
        explodedInputs = jobInputsProcessor.resolveJobInputs(explodedInputs);
        jobConfig.setInputs(explodedInputs.getValuesMap());

        Job job = buildNewJob(jobId, jobConfig);
        job = persistJob(job);

        JobSubmissionRequest jobSubmissionRequest;
        try {
            validateJob(job, explodedInputs);
            jobSubmissionRequest = buildJobSubmissionRequest(job, explodedInputs);
        } catch (Exception e) {
            LOG.error("Failed to submit job with ID {}", job.getId(), e);
            endJobWithError(job);
            throw e;
        }
        return jobSubmissionRequest;
    }

    private List<JobSubmissionRequest> buildSubJobSubmissionRequests(Job parentJob, JobInputs jobInputs) {
        String parallelInputsKey = getParallelInputsKey(parentJob);
        JobInput parallelInput = jobInputs.get(parallelInputsKey);

        List<JobSubmissionRequest> subJobSubmissionRequests = new ArrayList<>();
        for (String originalParallelInputValue : parallelInput.getValues()) {
            List<String> resolvedParallelInput = jobInputsProcessor.resolveUri(
                    originalParallelInputValue, jobInputs.getUserName());
            JobConfig subJobConfig = createJobConfigForSubJob(parentJob, resolvedParallelInput, parallelInputsKey);
            Job subJob = new Job(subJobConfig, UUID.randomUUID().toString(), parentJob.getOwner(), null);
            JobInputs parallelizedJobInputs =
                    jobInputs.cloneWithParallelizedInputs(parallelInputsKey, resolvedParallelInput);
            try {
                validateJob(subJob, parallelizedJobInputs);
                jobResourceManagementService.validateResourceRequest(
                        subJob.getOwner(), subJob.getConfig().getService().getRequiredResources());
            } catch (ServiceExecutionException see) {
                LOG.error("Validation for sub job " + subJob.getExtId() + " failed, setting sub job status in ERROR", see);
                subJob.setStatus(Job.Status.ERROR);
                persistJob(subJob);
                continue;
            }
            subJob = persistJob(subJob);
            subJobSubmissionRequests.add(buildJobSubmissionRequest(subJob, parallelizedJobInputs));
        }

        assertAtLeastOneValidJob(parentJob, subJobSubmissionRequests);

        return subJobSubmissionRequests;
    }

    private JobConfig createJobConfigForSubJob(Job parentJob, List<String> parallelInputValue, String parallelInputsKey) {
        SetMultimap<String, String> childInputs = MultimapBuilder.hashKeys().hashSetValues().build(parentJob.getConfig().getInputs());
        setSubJobInputs(childInputs, parallelInputValue, parallelInputsKey);
        JobConfig parentJobConfig = parentJob.getConfig();
        return new JobConfigBuilder(parentJobConfig.getOwner(), parentJobConfig.getService())
                .withLabel(parentJobConfig.getLabel())
                .withInputs(childInputs)
                .withParentJob(parentJob)
                .build();
    }

    private JobConfig buildJobConfig(JobLaunchRequest request, PlatformService service, Job parentJob) {
        User owner = userDataService.getByName(request.getUserId());

        return new JobConfigBuilder(owner, service)
                .withLabel(request.getJobConfigLabel())
                .withInputs(mapParams(request.getInputList()))
                .withParentJob(parentJob)
                .build();
    }

    private Job loadParentJobIfPresent(String parentId) {
        if (Strings.isNullOrEmpty(parentId)) {
            return null;
        }
        LOG.info("Attaching job to parent {}", parentId);
        return jobDataService.refreshFull(Long.valueOf(parentId))
                .orElseThrow(() -> new PlatformEntityNotFoundException("Failed to load parent Job with ID: " + parentId));
    }

    private static void setSubJobInputs(SetMultimap<String, String> subJobInputs, List<String> newInput,
                                        String parallelInputsKey) {
        if (PARALLEL_INPUTS_LEGACY_KEY.equals(parallelInputsKey)) {
            subJobInputs.removeAll(parallelInputsKey);
            subJobInputs.putAll("input", newInput);
            return;
        }
        subJobInputs.replaceValues(parallelInputsKey, newInput);
    }

    private Job buildNewJob(String extId, JobConfig jobConfig) {
        Job job = new Job(jobConfig, extId, jobConfig.getOwner());
        job.setParent(false);
        return job;
    }

    private Job buildParentJob(String extId, JobConfig jobConfig) {
        Job parentJob = new Job(jobConfig, extId, jobConfig.getOwner());
        parentJob.setParent(true);
        return parentJob;
    }

    private Job persistJob(Job job) {
        Job parent = job.getConfig().getParent();
        if (parent != null) {
            parent = persistJob(parent);
        }
        jobConfigDataService.save(job.getConfig());
        if (parent != null) {
            job.setParentJob(job.getConfig().getParent());
        }
        job = jobDataService.save(job);
        return job;
    }

    private JobSubmissionRequest buildJobSubmissionRequest(Job job, JobInputs jobInputs) {
        JobResourceRequirement jobResourceRequirement = jobResourceManagementService.evaluateResourceRequest(
                job.getOwner(), job.getConfig().getService().getRequiredResources());

        return JobSubmissionRequest.builder()
                .job(job)
                .jobResourceRequirement(jobResourceRequirement)
                .jobInputs(jobInputs)
                .build();
    }

    private void validateJob(Job job, JobInputs explodedInputs) {
        JobValidationResult validationResult = jobValidator.validate(job, explodedInputs);
        if (!validationResult.isValid()) {
            LOG.error(validationResult.getErrorMessage());
            throw new ServiceExecutionException(validationResult.getErrorMessage());
        }
    }

    private void assertAtLeastOneValidJob(Job parentJob, List<JobSubmissionRequest> subJobSubmissionRequests) {
        if (!subJobSubmissionRequests.isEmpty()) {
            return;
        }

        endJobWithError(parentJob);
        String invalidJobsMessage = String.format("No valid sub jobs exist for parent job with ID: %s", parentJob.getId());
        LOG.error(invalidJobsMessage);
        throw new ServiceExecutionException(invalidJobsMessage);
    }

    private void endJobWithError(Job job) {
        job.setStatus(Job.Status.ERROR);
        job.setEndTime(LocalDateTime.now());
        jobDataService.save(job);
    }

    private static String getParallelInputsKey(Job job) {
        return getParallelInputsKey(job.getConfig().getService().getServiceDescriptor());
    }

    private static String getParallelInputsKey(PlatformServiceDescriptor platformServiceDescriptor) {
        return hasParallelInputsKey(platformServiceDescriptor) ?
                platformServiceDescriptor.getParallelInputsKey() : PARALLEL_INPUTS_LEGACY_KEY;
    }

    private static boolean hasParallelInputsKey(PlatformServiceDescriptor platformServiceDescriptor) {
        return platformServiceDescriptor != null && platformServiceDescriptor.getParallelInputsKey() != null;
    }

    private Multimap<String, String> mapParams(List<JobLaunchRequest.Param> inputParams) {
        Multimap<String, String> inputs = ArrayListMultimap.create();
        inputParams.forEach(param -> inputs.putAll(param.getName(), param.getValue()));
        return inputs;
    }

    private static List<String> systematicParameterValues(JobLaunchRequest request, String systematicParameter) {
        List<String> values = request.getInputList().stream()
                .filter(param -> param.getName().equals(systematicParameter))
                .flatMap(param -> param.getValue().stream())
                .collect(Collectors.toList());
        if (values.isEmpty()) {
            throw new ServiceExecutionException(
                    "Systematic parameter '" + systematicParameter + "' not found in launch request inputs");
        }
        return values;
    }
}