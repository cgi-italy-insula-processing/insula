package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;


import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Resources {

	private Map<String, String> requests = new HashMap<>();
	
	private Map<String, String> limits = new HashMap<>();
}
