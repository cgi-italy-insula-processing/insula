package com.cgi.eoss.platform.core.processing.server.api.projections;

import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.utils.Identifiable;
import org.springframework.data.rest.core.config.Projection;

/**
 * <p>Detailed JSON projection for {@link PlatformService}s, including the service descriptor.</p>
 */
@Projection(name = "detailedPlatformService", types = {PlatformService.class})
public interface DetailedPlatformService extends Identifiable<Long> {

    String getName();

    String getDescription();

    PlatformService.Type getType();

    String getDockerTag();

    PlatformService.Licence getLicence();

    PlatformService.Status getStatus();

    Cwl getCwl();

    PlatformServiceDescriptor getServiceDescriptor();
}