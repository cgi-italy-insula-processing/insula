package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workflow {

	private final String apiVersion = "argoproj.io/v1alpha1";
	
	private final String kind = "Workflow";
	
	private Metadata metadata;
	
	private Spec spec;
	
	private Status status;
	
}
