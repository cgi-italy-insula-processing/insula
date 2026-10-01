package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model;

import lombok.*;

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
