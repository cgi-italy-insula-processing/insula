package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StacDocument extends Extensible {

    private String type;

    private List<StacItem> features;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class StacItem extends Extensible {

        @JsonProperty("stac_version")
        private String stacVersion;

        private String id;

        private String type;

        private Object geometry;

        private Map<String, Object> properties;

        private Map<String, Asset> assets;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class Asset extends Extensible {

        private URI href;

        private String type;

        private List<String> roles;

    }
}

@Data
class Extensible {

    @JsonAnyGetter
    @JsonAnySetter
    protected final Map<String, Object> other = new LinkedHashMap<>();

}
