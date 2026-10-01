package com.cgi.eoss.platform.core.processing.server.persistence.exceptions;

/**
 * Signals that an exception occurred when trying to retrieve a given Platform Entity.
 */
public class PlatformEntityNotFoundException extends RuntimeException{

    /**
     * Constructs a new PlatformEntityNotFound execution exception with the given detail message.
     *
     * @param message message describing the exception
     */
    public PlatformEntityNotFoundException(String message) {
        super(message);
    }
}
