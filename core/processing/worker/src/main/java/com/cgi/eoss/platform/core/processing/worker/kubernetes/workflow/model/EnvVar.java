package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvVar {

	private String name = null;
	private String value= null;
	private ValueFrom valueFrom = null;

	public EnvVar(String name, String value){
		this.name = name;
		this.value = value;
	}

	public EnvVar(String name, ValueFrom valueFrom){
		this.name = name;
		this.valueFrom = valueFrom;
	}
}
