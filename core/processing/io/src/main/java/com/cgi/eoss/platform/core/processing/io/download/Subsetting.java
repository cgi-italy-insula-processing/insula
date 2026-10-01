package com.cgi.eoss.platform.core.processing.io.download;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Class that holds the details of a Subsetting, used to request a subset of a product to be downloaded
 */
@Jacksonized @Builder
@Value
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Subsetting {

    @JsonProperty("aoi")
    private String aoi;

    @JsonProperty("format")
    private String format;
}