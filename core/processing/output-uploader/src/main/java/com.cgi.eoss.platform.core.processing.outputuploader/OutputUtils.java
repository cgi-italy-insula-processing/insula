package com.cgi.eoss.platform.core.processing.outputuploader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to handle deserialization of outputs parameters
 */
public class OutputUtils {

    private static final TypeReference<Map<String, String>> OUTPUTS_TYPE_REFERENCE = new TypeReference<Map<String, String>>() {};

    /**
     * Deserialize output parameters from a json string
     * @param outputs the JSON string containing the output parameters
     * @return serialized map, empty if the input string is null
     */
    public static Map<String, String> deserializeOutputs(String outputs) {
        if (outputs == null) {
            return new HashMap<>();
        }
        return deserialize(outputs);
    }

    private static Map<String, String> deserialize(String outputs) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(outputs, OUTPUTS_TYPE_REFERENCE);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
