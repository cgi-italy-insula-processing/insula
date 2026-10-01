package com.cgi.eoss.platform.core.processing.server.api.projections;

import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.utils.Identifiable;
import org.springframework.data.rest.core.config.Projection;

/**
 * <p>Default JSON projection for embedded {@link PlatformService}s. </p>
 */
@Projection(name = "shortPlatformService", types = {PlatformService.class})
public interface ShortPlatformService extends Identifiable<Long> {

    String getName();

    String getDescription();

    PlatformService.Type getType();

    String getDockerTag();

    PlatformService.Licence getLicence();

    PlatformService.Status getStatus();

    Cwl getCwl();

}
