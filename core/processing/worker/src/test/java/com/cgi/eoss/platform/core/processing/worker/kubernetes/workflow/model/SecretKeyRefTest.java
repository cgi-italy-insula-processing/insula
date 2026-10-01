package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.SecretKeyRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

public class SecretKeyRefTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final SecretKeyRef SECRET_KEY_REF_ALL_ATTRS = new SecretKeyRef("secretName", "secretKey");

    private static final String SECRET_KEY_REF_ALL_ATTRS_JSON = "{\"name\":\"secretName\", \"key\":\"secretKey\" }";

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(SECRET_KEY_REF_ALL_ATTRS),
                SECRET_KEY_REF_ALL_ATTRS_JSON,
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(new SecretKeyRef()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(SECRET_KEY_REF_ALL_ATTRS_JSON,SecretKeyRef.class))
                .isEqualTo(SECRET_KEY_REF_ALL_ATTRS);
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        SecretKeyRef secretKeyRef = OBJECT_MAPPER.readValue(
                "{\"name\":\"secretName\", \"key\":\"secretKey\", \"unknownKey\":\"unknownValue\" }",
                SecretKeyRef.class
        );

        assertThat(secretKeyRef).isEqualTo(SECRET_KEY_REF_ALL_ATTRS);
    }
}
