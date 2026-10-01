package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a collection of Kubernetes job params, each defined by a name, type, a list of values, and an optional subsetting.
 */
@Value
public class K8sJobParams {

    private final Map<String, ParamValue> jobParams = new HashMap<>();

    /**
     * Adds a new parameter.
     *
     * @param paramName  the name of the parameter.
     * @param type       the type of the parameter, default "OTHER" if empty or null
     * @param values     a list of values associated with the paramName, null is replaced with empty list
     */
    public void put(String paramName, String type, List<String> values) {
        put(paramName, type, values, null);
    }

    /**
     * Adds a new parameter.
     *
     * @param paramName  the name of the parameter.
     * @param type       the type of the parameter, default "OTHER" if empty or null
     * @param values     a list of values associated with the paramName, null is replaced with empty list
     * @param subsetting subsetting information indicating a subset of the parameter
     */
    public void put(String paramName, String type, List<String> values, Subsetting subsetting) {

        jobParams.put(
                paramName,
                ParamValue.builder()
                        .type(getIfNotEmptyOrDefault(type, "OTHER"))
                        .values(values == null ? ImmutableList.of() : ImmutableList.copyOf(values))
                        .subsetting(subsetting)
                        .build()
        );
    }

    private static String getIfNotEmptyOrDefault(String value, String defaultValue) {

        if (value == null || value.isEmpty()) {

            return defaultValue;
        }

        return value;
    }

    /**
     * Represents a single parameter with a specific type, a list of associated values, and an optional subsetting.
     */
    @Builder
    @Value
    public static class ParamValue {

        private final String type;
        private final List<String> values;
        private final Subsetting subsetting;
    }

    /**
     * Represents the criteria to retrieve a subset of a given EO product.
     */
    @Jacksonized
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subsetting {

        private final String aoi;
        private final String format;
    }
}
