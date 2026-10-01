package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@EnableConfigurationProperties(LegacyWorkflowProperties.class)
public abstract class LegacyWorkflowPropertiesIT {

    @Autowired
    protected LegacyWorkflowProperties legacyWorkflowProperties;

    @TestPropertySource(properties = {
            "platform.worker.workflow.legacy.deleteOnFailure=true",
            "platform.worker.workflow.legacy.namespace=namespace",
            "platform.worker.workflow.legacy.internalPlatformRegistryHost=internalPlatformRegistryHost",
            "platform.worker.workflow.legacy.externalRegistryImagePullSecretName=externalRegistryImagePullSecretName",
            "platform.worker.workflow.legacy.serviceAccountName=serviceAccountName",
            "platform.worker.workflow.legacy.inputDownloader.imageName=inputDownloaderImageName",
            "platform.worker.workflow.legacy.outputUploader.imageName=outputUploaderImageName",
            "platform.worker.workflow.legacy.persistentVolumeClaimStorageClass=persistentVolumeClaimStorageClass",
            "platform.worker.workflow.legacy.internalPlatformRegistryPrefix=internalPlatformRegistryPrefix",
            "platform.worker.workflow.legacy.internalPlatformRegistryImagePullSecretName=internalPlatformRegistryImagePullSecretName",
            "platform.worker.workflow.legacy.inputDownloader.envs[0].name=lg_indw_name_0",
            "platform.worker.workflow.legacy.inputDownloader.envs[0].valueFrom.secret.name=lg_indw_secret_name_0",
            "platform.worker.workflow.legacy.inputDownloader.envs[0].valueFrom.secret.key=lg_indw_secret_key_0",
            "platform.worker.workflow.legacy.inputDownloader.envs[1].name=lg_indw_name_1",
            "platform.worker.workflow.legacy.inputDownloader.envs[1].valueFrom.secret.name=lg_indw_secret_name_1",
            "platform.worker.workflow.legacy.inputDownloader.envs[1].valueFrom.secret.key=lg_indw_secret_key_1",
            "platform.worker.workflow.legacy.processing.envs[0].name=lg_pr_name_0",
            "platform.worker.workflow.legacy.processing.envs[0].valueFrom.secret.name=lg_pr_secret_name_0",
            "platform.worker.workflow.legacy.processing.envs[0].valueFrom.secret.key=lg_pr_secret_key_0",
            "platform.worker.workflow.legacy.outputUploader.envs[0].name=lg_op_name_0",
            "platform.worker.workflow.legacy.outputUploader.envs[0].valueFrom.secret.name=lg_op_secret_name_0",
            "platform.worker.workflow.legacy.outputUploader.envs[0].valueFrom.secret.key=lg_op_secret_key_0",
            "platform.worker.workflow.legacy.podScheduling.rules[0].condition=ram <=4  && gpus <=4",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[0].key=Node",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[0].operator=Equal",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[0].value=workflow",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[0].effect=NoSchedule",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[1].key=Pod",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[1].operator=NotEqual",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[1].value=PodFlow",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.podTolerations[1].effect=AllSchedule",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.nodeSelectors[0].key=legacy/nodeSelectorKey",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.nodeSelectors[0].value=legacy/nodeSelectorValue",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.nodeSelectors[1].key=public/legacy/nodeSelectorKey",
            "platform.worker.workflow.legacy.podScheduling.rules[0].action.nodeSelectors[1].value=public/legacy/nodeSelectorValue",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[0].key=Node",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[0].operator=Equal",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[0].value=workflow",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[0].effect=NoSchedule",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[1].key=Pod",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[1].operator=NotEqual",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[1].value=PodFlow",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.podTolerations[1].effect=AllSchedule",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.nodeSelectors[0].key=default/nodeSelectorKey",
            "platform.worker.workflow.legacy.podScheduling.defaultAction.nodeSelectors[0].value=default/nodeSelectorValue",
            "platform.worker.workflow.legacy.inputDownloaderConfigMapName=inputDownloaderConfigMapName",
            "platform.worker.workflow.legacy.outputUploaderConfigMapName=outputUploaderConfigMapName"

    })
    public static class LegacyWorkflowPropertiesCustomValuesIT extends LegacyWorkflowPropertiesIT {

        @Test
        public void testLegacyWorkflowPropertiesCustomValues() {

            assertThat(legacyWorkflowProperties.getInternalPlatformRegistryPrefix()).isEqualTo("internalPlatformRegistryPrefix");
            assertThat(legacyWorkflowProperties.getInternalPlatformRegistryImagePullSecretName()).isEqualTo("internalPlatformRegistryImagePullSecretName");
            assertThat(legacyWorkflowProperties.getExternalRegistryImagePullSecretName()).isEqualTo("externalRegistryImagePullSecretName");
            assertThat(legacyWorkflowProperties.getInputDownloader().getImageName()).isEqualTo("inputDownloaderImageName");
            assertThat(legacyWorkflowProperties.getOutputUploader().getImageName()).isEqualTo("outputUploaderImageName");
            assertThat(legacyWorkflowProperties.getPersistentVolumeClaimStorageClass()).isEqualTo("persistentVolumeClaimStorageClass");
            assertThat(legacyWorkflowProperties.getDeleteOnFailure()).isEqualTo(true);

            assertThat(legacyWorkflowProperties.getNamespace()).isEqualTo("namespace");

            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs()).hasSize(2);
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs().get(0).getName()).isEqualTo("lg_indw_name_0");
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs().get(0).getValueFrom().getSecret().getName()).isEqualTo("lg_indw_secret_name_0");
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs().get(0).getValueFrom().getSecret().getKey()).isEqualTo("lg_indw_secret_key_0");
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs().get(1).getName()).isEqualTo("lg_indw_name_1");
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs().get(1).getValueFrom().getSecret().getName()).isEqualTo("lg_indw_secret_name_1");
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs().get(1).getValueFrom().getSecret().getKey()).isEqualTo("lg_indw_secret_key_1");

            assertThat(legacyWorkflowProperties.getProcessing().getEnvs()).hasSize(1);
            assertThat(legacyWorkflowProperties.getProcessing().getEnvs().get(0).getName()).isEqualTo("lg_pr_name_0");
            assertThat(legacyWorkflowProperties.getProcessing().getEnvs().get(0).getValueFrom().getSecret().getName()).isEqualTo("lg_pr_secret_name_0");
            assertThat(legacyWorkflowProperties.getProcessing().getEnvs().get(0).getValueFrom().getSecret().getKey()).isEqualTo("lg_pr_secret_key_0");

            assertThat(legacyWorkflowProperties.getOutputUploader().getEnvs()).hasSize(1);
            assertThat(legacyWorkflowProperties.getOutputUploader().getEnvs().get(0).getName()).isEqualTo("lg_op_name_0");
            assertThat(legacyWorkflowProperties.getOutputUploader().getEnvs().get(0).getValueFrom().getSecret().getName()).isEqualTo("lg_op_secret_name_0");
            assertThat(legacyWorkflowProperties.getOutputUploader().getEnvs().get(0).getValueFrom().getSecret().getKey()).isEqualTo("lg_op_secret_key_0");

            assertThat(legacyWorkflowProperties.getInputDownloaderConfigMapName()).isEqualTo("inputDownloaderConfigMapName");
            assertThat(legacyWorkflowProperties.getOutputUploaderConfigMapName()).isEqualTo("outputUploaderConfigMapName");

        }
    }

    public static class LegacyWorkflowPropertiesDefaultValuesIT extends LegacyWorkflowPropertiesIT {

        @Test
        public void testLegacyWorkflowPropertiesDefaultValues() {
            assertThat(legacyWorkflowProperties.getInternalPlatformRegistryPrefix()).isEqualTo("registry");
            assertThat(legacyWorkflowProperties.getInternalPlatformRegistryImagePullSecretName()).isEqualTo("internalRegistrySecret");
            assertThat(legacyWorkflowProperties.getExternalRegistryImagePullSecretName()).isEqualTo("externalRegistrySecret");
            assertThat(legacyWorkflowProperties.getInputDownloader().getImageName()).isNull();
            assertThat(legacyWorkflowProperties.getOutputUploader().getImageName()).isNull();
            assertThat(legacyWorkflowProperties.getPersistentVolumeClaimStorageClass()).isNull();
            assertThat(legacyWorkflowProperties.getDeleteOnFailure()).isEqualTo(true);
            assertThat(legacyWorkflowProperties.getInputDownloaderConfigMapName()).isEqualTo("");
            assertThat(legacyWorkflowProperties.getOutputUploaderConfigMapName()).isEqualTo("");
            assertThat(legacyWorkflowProperties.getNamespace()).isEqualTo("default");
            assertThat(legacyWorkflowProperties.getInputDownloader().getEnvs()).hasSize(0);
            assertThat(legacyWorkflowProperties.getProcessing().getEnvs()).hasSize(0);
            assertThat(legacyWorkflowProperties.getOutputUploader().getEnvs()).hasSize(0);
        }
    }
}
