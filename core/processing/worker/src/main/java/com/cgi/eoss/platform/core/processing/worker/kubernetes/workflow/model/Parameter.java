package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
@Jacksonized @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Parameter {

	@NonNull
	private String name;
	
	private String value;
	
	
}
