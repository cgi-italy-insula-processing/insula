package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.download.Subsetting;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for parsing Kubernetes parameters.
 * It can filter parameters based on their type.
 */
public class K8sJobParams {

    private static final TypeReference<Map<String, InputValue>> TYPE_REFERENCE_STRING_INPUTVALUE =
            new TypeReference<Map<String, InputValue>>() {
            };

    private final Map<String, InputValue> k8Params;

    /**
     * Creates an instance of this class with the provided Inputs ands Mapper
     *
     * @param jsonInputs the parameters as a JSON string.
     * @param mapper the object mapper for parsing JSON
     */
    public K8sJobParams(String jsonInputs, ObjectMapper mapper) {
        k8Params = deserialize(jsonInputs, mapper);
    }

    /**
     * Retrieves a list of downloadable parameters
     *
     * @return a {@link List} of downloadable parameters
     */
    public List<DownloadableParam> getDownloadableParams() {

        List<DownloadableParam> downloadableParams = new ArrayList<>();

        k8Params.forEach((key, inputValue) -> {
            if ("URL".equals(inputValue.getType())) {

                DownloadableParam downloadableParam = DownloadableParam.builder()
                        .paramName(key)
                        .values(inputValue.getValues())
                        .subsetting(inputValue.getSubsetting())
                        .build();

                downloadableParams.add(downloadableParam);
            }
        });

        return downloadableParams;
    }

    /**
     * Retrieves a multimap of all parameters.
     *
     * @return a {@link Multimap} of all parameters.
     */
    public Multimap<String, String> getParams() {

        return filterParamsBy(null);
    }

    /**
     * Retrieves a multimap of STAC catalog parameters
     *
     * @return a {@link Multimap} of STAC parameters
     */
    public Multimap<String, String> getStacParams() {
        return filterParamsBy("STAC");
    }

    private Multimap<String, String> filterParamsBy(String filterBy) {
        Multimap<String, String> params = ArrayListMultimap.create();
        k8Params.forEach((key, inputValue) -> {
            if (filterBy == null || filterBy.equals(inputValue.getType())) {
                params.putAll(key, inputValue.getValues());
            }
        });

        return params;
    }

    private static Map<String, InputValue> deserialize(String jsonInputs, ObjectMapper mapper) {

        try {
            return mapper.readValue(jsonInputs, TYPE_REFERENCE_STRING_INPUTVALUE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Data
    private static class InputValue {

        private String type;

        private List<String> values;

        private Subsetting subsetting;

    }

    /**
     * Represents a single parameter of type "URL", with a list of associated values and an optional subsetting.
     */
    @Jacksonized @Builder
    @Value
    public static class DownloadableParam {

        private String paramName;

        private List<String> values;

        private Subsetting subsetting;

    }
}
