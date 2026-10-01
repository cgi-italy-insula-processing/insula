package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Step {

	private String name;
	
	private String template;
	
	private Arguments arguments;
}
