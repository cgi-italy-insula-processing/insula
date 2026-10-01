package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.exceptions;

/**
 * Exception that is thrown when the validation of a CWL does not succeed.
 */
public class CwlValidationException extends RuntimeException {

    /**
     * Creates an instance of this exception with the provided error message.
     * @param message The error message.
     */
    public CwlValidationException(String message) { super(message); }
}
