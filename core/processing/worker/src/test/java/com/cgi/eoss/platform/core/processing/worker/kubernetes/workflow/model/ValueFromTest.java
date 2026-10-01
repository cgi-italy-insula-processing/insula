package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.SecretKeyRef;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.ValueFrom;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueFromTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ValueFrom VALUE_FROM_ALL_ATTRS = new ValueFrom(new SecretKeyRef("secretName", "secretKey"));

    private static final String VALUE_FROM_ALL_ATTRS_JSON = "{\"secretKeyRef\" : {\"name\":\"secretName\", \"key\":\"secretKey\" } }";

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(VALUE_FROM_ALL_ATTRS),
                VALUE_FROM_ALL_ATTRS_JSON,
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(new ValueFrom()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(VALUE_FROM_ALL_ATTRS_JSON,ValueFrom.class))
                .isEqualTo(VALUE_FROM_ALL_ATTRS);
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        ValueFrom valueFrom = OBJECT_MAPPER.readValue(
                "{\"secretKeyRef\" : {\"name\":\"secretName\", \"key\":\"secretKey\" }, \"unknownKey\":\"unknownValue\" }",
                ValueFrom.class
        );

        assertThat(valueFrom).isEqualTo(VALUE_FROM_ALL_ATTRS);
    }
}
