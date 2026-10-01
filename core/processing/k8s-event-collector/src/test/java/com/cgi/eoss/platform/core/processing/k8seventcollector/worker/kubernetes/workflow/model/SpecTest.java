package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;

public class SpecTest {

    private static final Path MODEL_TEST_PATH = Paths.get("src", "test", "resources", "model");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(createSpecWithAllAttributes()),
                readAsString(MODEL_TEST_PATH.resolve("spec-with-all-attributes.json")),
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(new Spec()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(readAsString(MODEL_TEST_PATH.resolve("spec-with-all-attributes-and-config-map.json")),
                        Spec.class))
                .isEqualTo(createSpecWithAllAttributes());
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        Spec valueFrom = OBJECT_MAPPER.readValue(
                readAsString(MODEL_TEST_PATH.resolve("spec-with-all-attributes-and-unknown-key-and-config-map.json")),
                Spec.class
        );

        assertThat(valueFrom).isEqualTo(createSpecWithAllAttributes());
    }

    private Spec createSpecWithAllAttributes() {
        Spec spec = new Spec();
        spec.setEntrypoint("entrypoint");
        spec.setServiceAccountName("serviceAccountName");
        Arguments arguments = new Arguments();
        arguments.setParameters(ImmutableList.of(new Parameter("name", "value")));
        spec.setArguments(arguments);
        Template template = new Template();
        template.setName("template");
        spec.setTemplates(ImmutableList.of(template));
        Volume volume = new Volume();
        volume.setName("volume");
        spec.setVolumes(ImmutableList.of(volume));
        spec.setImagePullSecrets(ImmutableList.of(new ImagePullSecret("imagePullSecret")));
        VolumeClaimTemplate volumeClaimTemplate = new VolumeClaimTemplate();
        volumeClaimTemplate.setMetadata(ImmutableMap.of("claimKey", "claimValue"));
        spec.setVolumeClaimTemplates(ImmutableList.of(volumeClaimTemplate));
        return spec;
    }

}
