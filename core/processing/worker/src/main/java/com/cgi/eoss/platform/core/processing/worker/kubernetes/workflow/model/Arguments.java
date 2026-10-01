package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Arguments {

	private List<Parameter> parameters;
	private List<Artifact> artifacts;
}
