package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;


import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Jacksonized @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ImagePullSecret {

	private final String name;
}
