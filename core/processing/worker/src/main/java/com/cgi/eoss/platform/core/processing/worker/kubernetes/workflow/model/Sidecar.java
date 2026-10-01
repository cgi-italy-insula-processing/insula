package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Sidecar {

	private String name;
	
	private String image;
	
	private SecurityContext securityContext;
	
	private boolean mirrorVolumeMounts;
	
	private List<String> command = new ArrayList<>();

	private Resources resources;
}
