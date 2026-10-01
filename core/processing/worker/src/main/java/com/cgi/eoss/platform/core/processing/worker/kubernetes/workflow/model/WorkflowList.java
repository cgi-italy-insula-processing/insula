package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.annotations.SerializedName;

import lombok.Data;
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowList {

	@SerializedName("items")
	private List<Workflow> items = new ArrayList<>();
	
	private ListMeta metadata;
}
