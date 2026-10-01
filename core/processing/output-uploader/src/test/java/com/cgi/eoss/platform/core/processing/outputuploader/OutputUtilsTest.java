package com.cgi.eoss.platform.core.processing.outputuploader;

import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class OutputUtilsTest {

    @Test
    public void testDeserializeOutput_DeserializesProvidedString_WhenInputStringIsJSONRepresentingJobOutputs(){
        String jsonInput = "{\n" +
                "  \"output1\": \"value1\",\n" +
                "  \"output2\": \"value2\"\n" +
                "}";

        Map<String, String> deserializedOutputs = OutputUtils.deserializeOutputs(jsonInput);
        assertThat(deserializedOutputs.get("output1")).isEqualTo("value1");
        assertThat(deserializedOutputs.get("output2")).isEqualTo("value2");
        assertThat(deserializedOutputs.size()).isEqualTo(2);
    }

    @Test
    public void testDeserializeOutput_ThrowsIllegalArgumentException_WhenInputStringIsNotAValidJSON(){
        String jsonInput = "notAValidJSON";
        try {
            OutputUtils.deserializeOutputs(jsonInput);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Unrecognized token 'notAValidJSON'");
        }
    }

    @Test
    public void testDeserializeOutput_ReturnsEmptyMap_WhenInputStringIsNull(){
        Map<String, String> deserializedOutput = OutputUtils.deserializeOutputs(null);
        assertThat(deserializedOutput).isEmpty();
    }

}