package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
