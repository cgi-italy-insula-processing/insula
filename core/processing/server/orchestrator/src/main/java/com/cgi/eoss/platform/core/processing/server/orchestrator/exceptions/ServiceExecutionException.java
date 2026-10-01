package com.cgi.eoss.platform.core.processing.server.orchestrator.exceptions;

/**
 * <p>Signals that an exception occurred in the execution of an Platform Service.</p>
 */
public class ServiceExecutionException extends RuntimeException {

    /**
     * <p>Constructs a new service execution exception with the given detail message.</p>
     *
     * @param message
     */
    public ServiceExecutionException(String message) {
        super(message);
    }

    /**
     * <p>Constructs a new service execution exception with the specified detail message and cause.</p>
     * @param message the detail message
     * @param cause the cause. (A null value is permitted)
     */
    public ServiceExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

}
