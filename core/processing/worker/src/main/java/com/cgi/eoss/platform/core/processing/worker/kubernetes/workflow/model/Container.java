package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Container {
	
	private String image;
	
	private String name;
	
	private List<String> command = new ArrayList<>();
	
	private List<String> args = new ArrayList<>();
	
	private List<VolumeMount> volumeMounts= new ArrayList<>();
	
	private List<EnvVar> env = new ArrayList<>();
	
	private Resources resources;

	private String workingDir;

}
