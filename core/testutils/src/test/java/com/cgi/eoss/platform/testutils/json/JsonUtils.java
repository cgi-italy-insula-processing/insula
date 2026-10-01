package com.cgi.eoss.platform.testutils.json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readResourceAsString;

public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(new GuavaModule());
    }

    private JsonUtils() {
    }

    /**
     * Serialize the provided object into a JSON string
     *
     * @param value
     *            The object to serialize
     * @return
     *         A JSON string representing the input ojbect
     */
    public static String serialize(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> deserializeMap(String value) {
        try {
            return OBJECT_MAPPER.readValue(
                    value,
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T deserialize(Path path, Class<T> valueType) {
        try {
            return OBJECT_MAPPER.readValue(
                    path.toFile(),
                    valueType);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T deserialize(String value, Class<T> valueType) {
        try {
            return OBJECT_MAPPER.readValue(
                    value,
                    valueType);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String normalize(Path path) {
        return normalize(readResourceAsString(path));
    }

    public static String normalize(String json) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);
            return OBJECT_MAPPER.writeValueAsString(jsonNode);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
