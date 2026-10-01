package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchResponse;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobManager;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.common.collect.Multimap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobLaunchRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API extension for managing {@link JobConfig} resources.
 */
@RestController
@RequestMapping("/jobConfigs")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class JobConfigsApiExtension {

    private final JobDataService jobDataService;
    private final PlatformJobManager platformJobManager;

   /**
     * Launches a new job based on the provided job configuration.
     *
     * @param jobConfig the job configuration to use for launching the job
     * @return a response entity containing the launched job
     */
    @PostMapping("/{jobConfigId}/launch")
    public ResponseEntity<EntityModel<Job>> launch(@ModelAttribute("jobConfigId") JobConfig jobConfig) {

        JobLaunchResponse jobLaunchResponse = platformJobManager.launchJob(toJobLaunchRequest(jobConfig));

        return jobDataService.getById(Long.parseLong(jobLaunchResponse.getIntJobId()))
            .map(job -> ResponseEntity.ok(EntityModel.of(job)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static JobLaunchRequest toJobLaunchRequest(JobConfig jobConfig) {
        return JobLaunchRequest.builder()
                .jobId(UUID.randomUUID().toString())
                .userId(jobConfig.getOwner().getName())
                .serviceId(jobConfig.getService().getName())
                .jobConfigLabel(jobConfig.getLabel())
                .inputList(toJobInputParams(jobConfig.getInputs()))
                .build();
    }

    private static List<JobLaunchRequest.Param> toJobInputParams(Multimap<String, String> inputs) {
        return inputs.asMap().entrySet().stream()
                .map(entry -> JobLaunchRequest.Param.builder()
                        .name(entry.getKey())
                        .value(new ArrayList<>(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }
}
