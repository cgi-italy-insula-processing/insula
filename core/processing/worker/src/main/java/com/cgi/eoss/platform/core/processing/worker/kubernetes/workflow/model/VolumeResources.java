package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VolumeResources {
	
	private VolumeRequests requests;

}
