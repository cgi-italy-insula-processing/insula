package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Spec {
	
	private String entrypoint;
	
	private String serviceAccountName;

	private List<VolumeClaimTemplate> volumeClaimTemplates = new ArrayList<>();

    private VolumeClaimGC volumeClaimGC;

    private TtlStrategy ttlStrategy;
	
	private List <ImagePullSecret> imagePullSecrets = new ArrayList<>();
	
	private List<Template> templates = new ArrayList<>();
	
	private Arguments arguments;
	
	private List<Volume> volumes = new ArrayList<>();

	private Map<String, String> nodeSelector = null;

}
