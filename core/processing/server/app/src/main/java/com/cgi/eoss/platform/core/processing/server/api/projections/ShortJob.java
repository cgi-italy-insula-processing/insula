package com.cgi.eoss.platform.core.processing.server.api.projections;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.utils.Identifiable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;

import java.time.LocalDateTime;

/**
 * <p>Default JSON projection for embedded {@link Job}s. Embeds the owner as a ShortUser.</p>
 */
@Projection(name = "shortJob", types = {Job.class})
public interface ShortJob extends Identifiable<Long> {

    String getExtId();

    String getPhase();

    Job.Status getStatus();

    String getStage();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();

    boolean isParent();

    @Value("#{target.config.service.name}")
    String getServiceName();

    @Value("#{target.config.service.id}")
    Long getServiceId();

    @Value("#{target.config.label}")
    String getLabel();

}
