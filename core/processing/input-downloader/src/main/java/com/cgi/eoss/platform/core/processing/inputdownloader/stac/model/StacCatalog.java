package com.cgi.eoss.platform.core.processing.inputdownloader.stac.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class StacCatalog {
    @JsonProperty("stac_version")
    private String stacVersion;
    private String id;
    private String type;
    private String description;
    private List<StacLink> links;
}
