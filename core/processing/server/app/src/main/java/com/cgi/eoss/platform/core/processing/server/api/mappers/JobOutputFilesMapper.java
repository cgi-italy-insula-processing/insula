package com.cgi.eoss.platform.core.processing.server.api.mappers;

import com.cgi.eoss.platform.core.processing.server.api.controllers.JobsApiExtension;
import com.cgi.eoss.platform.core.processing.server.api.resources.JobOutputFileResource;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.Link;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Maps the output files of a job to their REST API representation.
 */
@RequiredArgsConstructor
public class JobOutputFilesMapper {

    private final JobDataService jobDataService;
    private final String apiBasePath;

    /**
     * Lists the output files of the provided job, one per file of every output, each one linked to the endpoint it
     * can be downloaded from. A parent job produces no file itself: its output files are those of its sub-jobs,
     * linked to the sub-job that produced them.
     *
     * @param job the job whose output files are listed
     * @return the output files of the job, in the order the job records them; empty when the job has recorded no
     *         output yet
     */
    public List<JobOutputFileResource> toJobOutputFileResources(Job job) {
        return jobOrSubJobs(job).stream()
                .flatMap(
                        producingJob -> toOwnJobOutputFileResources(producingJob).stream()
                ).collect(Collectors.toList());
    }

    private List<Job> jobOrSubJobs(Job job) {
        if (!job.isParent()) {
            return Collections.singletonList(job);
        }
        List<Long> subJobIds = jobDataService.getSubJobIds(job);
        return subJobIds.isEmpty() ? Collections.emptyList() : jobDataService.findByIds(subJobIds);
    }

    private List<JobOutputFileResource> toOwnJobOutputFileResources(Job producingJob) {
        if (producingJob.getOutputs() == null) {
            return Collections.emptyList();
        }
        return producingJob.getOutputs().entries().stream()
                .map(
                        output -> toJobOutputFileResource(producingJob, output.getKey(), output.getValue())
                ).collect(Collectors.toList());
    }

    private JobOutputFileResource toJobOutputFileResource(Job job, String outputId, String outputPath) {
        String outputFolder = job.getExtId() + "/" + outputId + "/";
        if (!outputPath.startsWith(outputFolder) || outputPath.length() == outputFolder.length()) {
            throw new IllegalStateException("Output path " + outputPath + " of job " + job.getId()
                    + " does not match the layout " + outputFolder + "<filename>");
        }
        String filename = outputPath.substring(outputFolder.length());
        return new JobOutputFileResource(filename)
                .add(Link.of(downloadUri(job.getId(), outputId, filename).toString(), "download"));
    }

    private URI downloadUri(Long jobId, String outputId, String filename) {
        URI endpoint = linkTo(methodOn(JobsApiExtension.class).downloadFile(jobId, outputId, filename)).toUri();
        return UriComponentsBuilder.fromUri(endpoint)
                .replacePath(apiBasePath + endpoint.getRawPath())
                .build(true)
                .toUri();
    }

}
