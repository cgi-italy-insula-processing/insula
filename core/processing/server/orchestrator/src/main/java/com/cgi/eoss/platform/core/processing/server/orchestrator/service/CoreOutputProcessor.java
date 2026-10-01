package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.cgi.eoss.platform.core.processing.server.model.Job;

import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core {@link OutputProcessor}: it repatriates the job output objects from the object storage (via
 * the {@link JobOutputsRepatriationService}).
 */
@AllArgsConstructor
@Slf4j
public class CoreOutputProcessor implements OutputProcessor {

    private final JobOutputsRepatriationService jobOutputsRepatriationService;

    private final JobDataService jobDataService;

    private final Path jobOutputsBasePath;

    @Override
    public void processOutputs(Job job) throws IOException {
        List<RepatriatedOutput> repatriatedOutputs = jobOutputsRepatriationService.repatriate(job);
        LOG.info("Repatriated {} output(s) for job {}", repatriatedOutputs.size(), job.getExtId());

        job.setOutputs(relativizeJobOutputsPaths(repatriatedOutputs));
        job = jobDataService.save(job);

        if (job.getParentJob() != null) {
            jobDataService.updateParentJobOutputs(job);
        }
    }

    private Multimap<String, String> relativizeJobOutputsPaths(List<RepatriatedOutput> repatriatedOutputs) {
        Multimap<String, String> outputFiles = ArrayListMultimap.create();
        for (RepatriatedOutput repatriatedOutput : repatriatedOutputs) {
            outputFiles.putAll(repatriatedOutput.getOutputId(), relativizeJobOutputPaths(repatriatedOutput.getRepatriatedPaths()));
        }
        return outputFiles;
    }

    private List<String> relativizeJobOutputPaths(List<Path> jobOutputPaths) {
        return jobOutputPaths
                .stream()
                .map(p -> jobOutputsBasePath.relativize(p).toString())
                .collect(Collectors.toList());
    }
}
