package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.validators;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

/**
 * Class that holds the CWL Validation results.
 */
@Value
@Builder
public class CwlValidationResult {

    private final boolean isValid;

    private final String validationMessage;
}
