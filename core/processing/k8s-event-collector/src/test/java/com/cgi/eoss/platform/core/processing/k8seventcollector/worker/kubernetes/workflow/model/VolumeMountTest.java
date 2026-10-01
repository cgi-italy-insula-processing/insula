package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

public class VolumeMountTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final VolumeMount VOLUME_MOUNT_FROM_ALL_ATTRS;

    private static final String VOLUME_MOUNT_FROM_ALL_ATTRS_JSON = "{\"name\": \"name\",\"mountPath\": \"/target\",\"subPath\": \"subPath\",\"readOnly\": true}";

    static {
        VOLUME_MOUNT_FROM_ALL_ATTRS = new VolumeMount();
        VOLUME_MOUNT_FROM_ALL_ATTRS.setMountPath("/target");
        VOLUME_MOUNT_FROM_ALL_ATTRS.setName("name");
        VOLUME_MOUNT_FROM_ALL_ATTRS.setSubPath("subPath");
        VOLUME_MOUNT_FROM_ALL_ATTRS.setReadOnly(true);
    }

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(VOLUME_MOUNT_FROM_ALL_ATTRS),
                VOLUME_MOUNT_FROM_ALL_ATTRS_JSON,
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(new VolumeMount()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(VOLUME_MOUNT_FROM_ALL_ATTRS_JSON, VolumeMount.class))
                .isEqualTo(VOLUME_MOUNT_FROM_ALL_ATTRS);
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        VolumeMount volumeMount = OBJECT_MAPPER.readValue(
                "{\"name\": \"name\",\"mountPath\": \"/target\",\"subPath\": \"subPath\",\"readOnly\": true}",
                VolumeMount.class
        );

        assertThat(volumeMount).isEqualTo(VOLUME_MOUNT_FROM_ALL_ATTRS);
    }
}
