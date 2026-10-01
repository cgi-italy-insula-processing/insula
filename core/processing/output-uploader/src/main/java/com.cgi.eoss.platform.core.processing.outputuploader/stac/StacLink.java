package com.cgi.eoss.platform.core.processing.outputuploader.stac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class defines the structure of a link object found in a STAC object
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StacLink {
    private String rel;
    private String href;
    private String type;
    private String title;
}