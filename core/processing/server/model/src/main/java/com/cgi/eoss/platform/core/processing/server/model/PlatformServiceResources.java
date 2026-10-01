package com.cgi.eoss.platform.core.processing.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Represents the hardware resource configuration for a platform service,
 * including CPU, RAM, storage, GPU, and shared memory details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformServiceResources {

    private String cpus;
    
    private String ram;

    private String storage;
    
    private String gpus;

    private SharedMemory sharedMemory;

    /**
     * Represents shared memory configuration for the platform service.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SharedMemory {

        private String size;
    }
}
