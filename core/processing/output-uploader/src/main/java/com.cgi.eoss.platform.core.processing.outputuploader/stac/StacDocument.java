package com.cgi.eoss.platform.core.processing.outputuploader.stac;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class defines the structure of a STAC document.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StacDocument {

    private List<StacItem> features;

    private Map<String, Object> otherProperties = new HashMap<>();
    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }

    @JsonAnySetter
    public void add(String key, Object value) {
        otherProperties.put(key, value);
    }

    /**
     * This class defines some basic attributes of a STAC item.
     * The otherProperties field contains all the possible properties inside the document that we are not
     * using at the moment.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StacItem {
        @JsonProperty("stac_version")
        private String stacVersion;

        private String id;

        private Map<String, Asset> assets;

        private Map<String, Object> otherProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getOtherProperties() {
            return otherProperties;
        }

        @JsonAnySetter
        public void add(String key, Object value) {
            otherProperties.put(key, value);
        }
    }

    /**
     * This class defines the structure of an Asset object inside a STAC item.
     * The otherProperties field contains all the information that we aren't using at the moment.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Asset {
        private Path href;
        private Map<String, Object> otherProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getOtherProperties() {
            return otherProperties;
        }

        @JsonAnySetter
        public void add(String key, Object value) {
            otherProperties.put(key, value);
        }

    }
}