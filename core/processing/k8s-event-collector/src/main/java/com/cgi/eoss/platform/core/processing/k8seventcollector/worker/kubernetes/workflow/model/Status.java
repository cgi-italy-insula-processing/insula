package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable class that models the workflow status
 *
 * @author cantaveneraf
 *
 */
@Value
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status {

    @JsonProperty("phase")
    private final String phase;

    @JsonProperty("startedAt")
    private final Instant startedAt;

    @JsonProperty("finishedAt")
    private final Instant finishedAt;

    /**
     * Builds an instance of this class from the values provided on the map.
     * Each key whose name matches the Status fields will be read and the
     * associated value assigned to the Status object field.
     * Fields without matching key will be assigned the null value
     *
     * @param map
     *            The map from which the Status object will be initialized
     * @return
     *         A Status object with fields initialized from the given map
     */
    public static Status from(Map<String, Object> map) {
        return new Status(
                getMapValue(map, "phase", String.class),
                getMapValueAsInstant(map, "startedAt"),
                getMapValueAsInstant(map, "finishedAt"));
    }

    private static Instant getMapValueAsInstant(Map<String, Object> map, String key) {
        Instant result = null;
        String instantString = getMapValue(map, key, String.class);
        if (instantString != null && !instantString.isEmpty()) {
            result = Instant.parse(instantString);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getMapValue(Map<String, Object> map, String key, Class<T> clazz) {
        return (T) map.getOrDefault(key, null);
    }

}
