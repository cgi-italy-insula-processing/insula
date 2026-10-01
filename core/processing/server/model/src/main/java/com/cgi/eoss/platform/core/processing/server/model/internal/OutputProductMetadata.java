package com.cgi.eoss.platform.core.processing.server.model.internal;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;

/**
 * <p>Convenience wrapper of metadata for a output data product.</p>
 */
@Data
@Builder
public class OutputProductMetadata {

    private User owner;
    private PlatformService service;
    private String outputId;
    private String jobId;
    private Map<String, Object> productProperties;

}
