package com.cgi.eoss.platform.core.processing.server.api.projections;

import com.cgi.eoss.platform.core.processing.server.api.resources.JobOutputFileResource;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.JobStep;
import com.cgi.eoss.platform.core.processing.server.model.utils.Identifiable;
import com.google.common.collect.Multimap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;

/**
 * <p>Comprehensive representation of a Job entity, including outputs, jobConfig and the output files with their
 * download links, for embedding in REST responses.</p>
 */
@Projection(name = "detailedJob", types = Job.class)
public interface DetailedJob extends Identifiable<Long> {

    String getExtId();

    JobStep getPhase();

    Job.Status getStatus();

    String getStage();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();

    OffsetDateTime getCreated();

    OffsetDateTime getLastUpdated();

    boolean isParent();

    @Value("#{@jpaJobDataService.countSubJobStatuses(target)}")
    Map<Job.Status, Long> getSubJobStatusCounts();

    @Value("#{target.config.service.name}")
    String getServiceName();

    @Value("#{target.config.service.id}")
    Long getServiceId();

    Multimap<String, String> getOutputs();

    JobConfig getConfig();

    @Value("#{@jobOutputFilesMapper.toJobOutputFileResources(target)}")
    List<JobOutputFileResource> getOutputFiles();
}
