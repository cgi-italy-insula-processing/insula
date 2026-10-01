package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

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
