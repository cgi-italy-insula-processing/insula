package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Arguments {

	private List<Parameter> parameters;
	private List<Artifact> artifacts;
}
