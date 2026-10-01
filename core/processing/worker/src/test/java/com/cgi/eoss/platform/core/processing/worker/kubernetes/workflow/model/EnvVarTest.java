package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.EnvVar;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.SecretKeyRef;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.ValueFrom;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
public class EnvVarTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final EnvVar ENV_VAR_VALUE_FROM_ALL_ATTRS;

    private static final String ENV_VAR_VALUE_FROM_ALL_ATTRS_JSON = "{\"name\": \"envVarName\", \"value\":\"envVarValue\", \"valueFrom\" : {\"secretKeyRef\" : {\"name\":\"secretName\", \"key\":\"secretKey\" } } }";

    static {
        ENV_VAR_VALUE_FROM_ALL_ATTRS = new EnvVar("envVarName", new ValueFrom(new SecretKeyRef("secretName", "secretKey")));
        ENV_VAR_VALUE_FROM_ALL_ATTRS.setValue("envVarValue");
    }

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(ENV_VAR_VALUE_FROM_ALL_ATTRS),
                ENV_VAR_VALUE_FROM_ALL_ATTRS_JSON,
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(new EnvVar()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(ENV_VAR_VALUE_FROM_ALL_ATTRS_JSON, EnvVar.class))
                .isEqualTo(ENV_VAR_VALUE_FROM_ALL_ATTRS);
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        EnvVar valueFrom = OBJECT_MAPPER.readValue(
                "{\"name\": \"envVarName\", \"value\":\"envVarValue\", \"valueFrom\" : {\"secretKeyRef\" : {\"name\":\"secretName\", \"key\":\"secretKey\" } }, \"unknownKey\":\"unknownValue\" }",
                EnvVar.class
        );

        assertThat(valueFrom).isEqualTo(ENV_VAR_VALUE_FROM_ALL_ATTRS);
    }
}
