package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


/**
 * Class that holds the properties to configure the Argo Workflow of the 'Legacy' processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "platform.worker.workflow.legacy")
public class LegacyWorkflowProperties {

    private String internalPlatformRegistryPrefix = "registry";
    private String internalPlatformRegistryImagePullSecretName = "internalRegistrySecret";
    private String externalRegistryImagePullSecretName = "externalRegistrySecret";
    private String persistentVolumeClaimStorageClass = null;
    private Boolean deleteOnFailure = true;
    private String namespace = "default";
    private String inputDownloaderConfigMapName = "";
    private String outputUploaderConfigMapName = "";
    private Step inputDownloader = new Step();
    private Step processing = new Step();
    private Step outputUploader = new Step();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {

        private List<Environment> envs = new ArrayList<>();
        private String imageName = null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Environment {

        private String name = null;
        private ValueFrom valueFrom = null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueFrom {
        private Secret secret = null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Secret {

        private String name = null;
        private String key = null;
    }
}
