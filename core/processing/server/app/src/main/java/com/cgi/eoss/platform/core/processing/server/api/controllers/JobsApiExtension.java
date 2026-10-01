package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.CancelJobRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.StopJobRequest;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobOutputsRetrievalService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.PlatformJobStopper;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Rest controller providing additional endpoints to interact with jobs
 */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class JobsApiExtension {

    private final PlatformJobStopper platformJobStopper;
    private final JobOutputsRetrievalService jobOutputsRetrievalService;

    /**
     * Terminates the execution of a running {@link Job}
     * @param job that should be terminated
     * @return a {@link org.springframework.http.HttpStatus#NO_CONTENT} response if the operation is successful
     */
    @PostMapping("/{jobId}/terminate")
    public ResponseEntity stop(@ModelAttribute("jobId") Job job) {
        platformJobStopper.stopJob(StopJobRequest.builder().intJobId(job.getId().toString()).build());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cancels a {@link Job} if has  not started yet
     * @param job for which the cancel is requested
     */
    @GetMapping("/{jobId}/cancel")
    @ResponseBody
    public void cancelJob(@ModelAttribute("jobId") Job job) {
        platformJobStopper.cancelJob(CancelJobRequest.builder().intJobId(job.getId().toString()).build());
    }

    /**
     * Describes the files of a {@link Job} output as a STAC Collection whose Items assets link to the downloadable
     * files. For parent jobs, the Collection aggregates the outputs of the sub-jobs.
     *
     * @param jobId The id of the job that produced the output
     * @param outputId The id of the job output to describe
     * @return The STAC Collection describing the files of the job output, as GeoJSON
     */
    @GetMapping(value = "/{jobId}/outputs/{outputId}", params = "!filename")
    public ResponseEntity<StacDocument> getJobOutputAsStacCollection(@PathVariable Long jobId,
                                                                    @PathVariable String outputId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(jobOutputsRetrievalService.retrieveAsStacCollection(jobId, outputId));
    }

    /**
     * Downloads one file of a {@link Job} output as an attachment named after the file.
     *
     * @param jobId The id of the job that produced the output
     * @param outputId The id of the job output the file belongs to
     * @param filename The name of the file to download, as referenced by the assets of the STAC Collection
     *                 describing the job output
     * @return The content of the file, typed after the file extension (octet-stream when unknown), with a
     *         Content-Disposition header naming the attachment after the file
     */
    @GetMapping(value = "/{jobId}/outputs/{outputId}", params = "filename")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long jobId, @PathVariable String outputId,
                                                 @RequestParam String filename) {
        Path file = jobOutputsRetrievalService.retrieveOutputFile(jobId, outputId, filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaTypeFactory.getMediaType(file.getFileName().toString())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(new FileSystemResource(file));
    }

}
