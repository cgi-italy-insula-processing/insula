package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder @Jacksonized
@AllArgsConstructor(onConstructor = @__(@JsonCreator))
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Parameter {

	@NonNull
	private String name;
	
	private String value;
	
	
}
