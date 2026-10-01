package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status {

	private String phase;
	
	private Instant startedAt;
	
	private Instant finishedAt;
}
