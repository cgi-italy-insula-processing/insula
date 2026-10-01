package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Requests {

	private String memory;
	
	private String cpu;
	
	@JsonProperty(value = "nvidia.com/gpu")
	private String gpu;
}
