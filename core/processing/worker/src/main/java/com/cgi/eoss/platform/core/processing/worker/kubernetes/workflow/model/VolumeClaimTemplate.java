package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VolumeClaimTemplate {

	private Map<String, String> metadata = new HashMap<>();
	
	private VolumeSpec spec;
}

