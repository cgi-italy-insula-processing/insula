package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {

	private Map<String, String> labels = new HashMap<>();

	private String name; 
	
	private String generateName;
}
