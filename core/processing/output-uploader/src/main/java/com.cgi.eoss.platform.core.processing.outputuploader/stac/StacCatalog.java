package com.cgi.eoss.platform.core.processing.outputuploader.stac;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * This class defines the base structure for a catalog in STAC format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StacCatalog {
    @JsonProperty("stac_version")
    private String stacVersion;
    private String id;
    private String type;
    private String description;
    private List<StacLink> links;
}
