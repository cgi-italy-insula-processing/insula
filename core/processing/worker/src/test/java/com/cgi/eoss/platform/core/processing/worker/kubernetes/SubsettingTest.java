package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.K8sJobParams.Subsetting;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

public class SubsettingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Subsetting SUBSETTING_ALL_ATTRS = Subsetting.builder().aoi("aoiValue").format("formatValue").build();

    private static final String SUBSETTING_ALL_ATTRS_JSON = "{\"aoi\":\"aoiValue\", \"format\":\"formatValue\" }";

    @Test
    public void testSerialize_SerializesAllJsonKeyValues_WhenAllAttributesAreSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(SUBSETTING_ALL_ATTRS),
                SUBSETTING_ALL_ATTRS_JSON,
                JSONCompareMode.STRICT);
    }

    @Test
    public void testSerialize_SerializesEmptyJson_WhenAttributesAreNotSet() throws Exception {
        JSONAssert.assertEquals(
                OBJECT_MAPPER.writeValueAsString(Subsetting.builder().build()),
                "{}",
                JSONCompareMode.STRICT);
    }


    @Test
    public void testDeserialize_DeserializesAllAttributes_WhenAllJsonKeyValuesAreSet() throws Exception {
        assertThat(
                OBJECT_MAPPER.readValue(SUBSETTING_ALL_ATTRS_JSON, Subsetting.class))
                .isEqualTo(SUBSETTING_ALL_ATTRS);
    }

    @Test
    public void testDeserialize_IgnoresUnknownJsonKeys() throws Exception {
        Subsetting subsetting = OBJECT_MAPPER.readValue(
                "{\"aoi\":\"aoiValue\", \"format\":\"formatValue\", \"unknownKey\":\"unknownValue\" }",
                Subsetting.class
        );

        assertThat(subsetting).isEqualTo(SUBSETTING_ALL_ATTRS);
    }
}
