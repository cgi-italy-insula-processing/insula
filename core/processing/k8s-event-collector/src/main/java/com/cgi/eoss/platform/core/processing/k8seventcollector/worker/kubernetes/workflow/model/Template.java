package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Template {
	
	private String name;
	
	private Container container;
	
	private Inputs inputs;
	
	private List<List<Step>> steps;
	
	private List<Sidecar> sidecars;
}
