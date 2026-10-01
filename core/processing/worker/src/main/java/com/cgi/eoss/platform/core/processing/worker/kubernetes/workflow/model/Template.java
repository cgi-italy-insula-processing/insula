package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Template {
	
	private String name;
	
	private Container container;

	private List<Toleration> tolerations;
	
	private Inputs inputs;
	
	private List<List<Step>> steps;
	
	private List<Sidecar> sidecars;
	
	private TemplateMetadata metadata;
}
