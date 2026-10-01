package com.cgi.eoss.platform.core.processing.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlatformServiceDockerBuildInfo {
    
    private String lastBuiltFingerprint;
    
    private String errorMessage;
    
    private Status dockerBuildStatus = Status.NOT_STARTED;
    
    public enum Status {
        NOT_STARTED, ONGOING, COMPLETED, ERROR
    }
}
