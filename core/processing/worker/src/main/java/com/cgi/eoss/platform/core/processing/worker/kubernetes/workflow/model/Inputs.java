package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Data;

@Data
@AllArgsConstructor
@Jacksonized @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Inputs {

	private List<Parameter> parameters = new ArrayList<>();
}
