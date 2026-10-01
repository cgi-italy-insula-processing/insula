package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Spec {
	
	private String entrypoint;
	
	private String serviceAccountName;

	private List<VolumeClaimTemplate> volumeClaimTemplates = new ArrayList<>();
	
	private List <ImagePullSecret> imagePullSecrets = new ArrayList<>();
	
	private List<Template> templates = new ArrayList<>();
	
	private Arguments arguments;
	
	private List<Volume> volumes = new ArrayList<>();
}
