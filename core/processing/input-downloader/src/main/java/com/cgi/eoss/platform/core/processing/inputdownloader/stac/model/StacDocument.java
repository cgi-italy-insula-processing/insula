package com.cgi.eoss.platform.core.processing.inputdownloader.stac.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class StacDocument {

    @JsonProperty("features")
    private List<StacItem> items;

    private Map<String, Object> otherProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }

    @JsonAnySetter
    public void add(String key, Object value) {
        otherProperties.put(key, value);
    }

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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Asset {
        private String href;
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
