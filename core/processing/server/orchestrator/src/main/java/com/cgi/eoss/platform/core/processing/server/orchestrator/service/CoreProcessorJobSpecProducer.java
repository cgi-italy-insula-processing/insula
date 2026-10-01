package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.Job.Status;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.Kind;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.transaction.Transactional;

/**
 * <p>
 * Processing core for processor job submission.
 * </p>
 */
@AllArgsConstructor
@Slf4j
public class CoreProcessorJobSpecProducer implements JobSpecProducer {

    private final JobDataService jobDataService;
    private final UserMountResolver userMountResolver;

    /**
     * Builds the grpc spec for a processor job and persists the job in {@link Status#PENDING}.
     *
     * @param jobSubmissionRequest the request holding the job, its inputs and its resource requirements
     * @return the built {@link JobSpec}, with kind {@link Kind#WORKFLOW}
     * @throws PlatformEntityNotFoundException if a user mount
     *         referenced by the service cannot be found
     */
    @Override
    @Transactional
    public JobSpec produceJobSpec(JobSubmissionRequest jobSubmissionRequest) {
        Job job = jobSubmissionRequest.getJob();
        job.setStatus(Status.PENDING);
        jobDataService.save(job);

        return buildJobSpec(jobSubmissionRequest, job);
    }

    @Override
    public boolean supports(PlatformService service) {
        PlatformService.Type type = service.getType();
        return type == PlatformService.Type.PROCESSOR
                || type == PlatformService.Type.PARALLEL_PROCESSOR
                || type == PlatformService.Type.BULK_PROCESSOR;
    }

    private JobSpec buildJobSpec(JobSubmissionRequest jobSubmissionRequest, Job job) {
        JobSpec.Builder jobSpecBuilder = JobSpecMapper.buildJobSpecBuilder(
                jobSubmissionRequest,
                userMountResolver.resolve(job.getConfig().getService()));
        jobSpecBuilder.setKind(Kind.WORKFLOW);
        return jobSpecBuilder.build();
    }

}