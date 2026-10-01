package com.cgi.eoss.platform.core.processing.server.orchestrator.exceptions;

/**
 * <p>Signals that an exception occurred during launch of a Platform Job.</p>
 */
public class JobLaunchException extends RuntimeException {

    /**
     * <p>Constructs a new job launch exception with the given detail message.</p>
     *
     * @param message the detail message
     */
    public JobLaunchException(String message) {
        super(message);
    }

    /**
     * <p>Constructs a new job launch exception with the specified detail message and cause.</p>
     *
     * @param message the detail message
     * @param cause the cause. (A null value is permitted)
     */
    public JobLaunchException(String message, Throwable cause) {
        super(message, cause);
    }

}