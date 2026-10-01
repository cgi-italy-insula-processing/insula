package com.cgi.eoss.platform.core.processing.inputdownloader.stac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class StacLink {
    private String rel;
    private String href;
    private String type;
    private String title;
}
