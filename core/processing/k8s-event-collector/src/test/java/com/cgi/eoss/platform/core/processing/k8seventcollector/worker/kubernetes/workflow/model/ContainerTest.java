package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Container CONTAINER_FROM_ALL_ATTRS;

    private static final String CONTAINER_FROM_ALL_ATTRS_JSON = "{\"image\": \"image\", \"name\": \"name\", " +
            "\"command\": [\"command\"], \"args\": [\"arg\"], " +
            "\"volumeMounts\": [{\"mountPath\": \"mountPath\" }], " +
            "\"env\": [{\"name\": \"envVarName\", \"value\": \"envVarValue\"}], \"resources\": {\"limits\": {\"cpu\": \"1\"}}, " +
            "\"workingDir\": \"workDir\"}";

    static {
        CONTAINER_FROM_ALL_ATTRS = new Container();
        CONTAINER_FROM_ALL_ATTRS.setImage("image");
        CONTAINER_FROM_ALL_ATTRS.setName("name");
        CONTAINER_FROM_ALL_ATTRS.setCommand(ImmutableList.of("command"));
        CONTAINER_FROM_ALL_ATTRS.setArgs(ImmutableList.of("arg"));
        VolumeMount volumeMount = new VolumeMount();
        volumeMount.setMountPath("mountPath");
        CONTAINER_FROM_ALL_ATTRS.setVolumeMounts(ImmutableList.of(volumeMount));
        EnvVar envVar = new EnvVar("envVarName", "envVarValue");
        CONTAINER_FROM_ALL_ATTRS.setEnv(ImmutableList.of(envVar));
        Resources resources = new Resources();
        resources.setLimits(ImmutableMap.of("cpu", "1"));
        CONTAINER_FROM_ALL_ATTRS.setResources(resources);
        CONTAINER_FROM_ALL_ATTRS.setWorkingDir("workDir");
    }

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(CONTAINER_FROM_ALL_ATTRS),
                CONTAINER_FROM_ALL_ATTRS_JSON,
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(new Container()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(CONTAINER_FROM_ALL_ATTRS_JSON, Container.class))
                .isEqualTo(CONTAINER_FROM_ALL_ATTRS);
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        Container container = OBJECT_MAPPER.readValue(
                "{\"image\": \"image\", \"name\": \"name\", " +
                        "\"command\": [\"command\"], \"args\": [\"arg\"], " +
                        "\"volumeMounts\": [{\"mountPath\": \"mountPath\" }], " +
                        "\"env\": [{\"name\": \"envVarName\", \"value\": \"envVarValue\"}], \"resources\": {\"limits\": {\"cpu\": \"1\"}}, " +
                        "\"workingDir\": \"workDir\", \"unknownKey\": \"unknownValue\"}",
                Container.class
        );

        assertThat(container).isEqualTo(CONTAINER_FROM_ALL_ATTRS);
    }
}
