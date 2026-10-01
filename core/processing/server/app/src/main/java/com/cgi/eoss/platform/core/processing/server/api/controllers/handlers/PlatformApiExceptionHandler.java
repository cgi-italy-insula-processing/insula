package com.cgi.eoss.platform.core.processing.server.api.controllers.handlers;

import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.io.UncheckedIOException;

/**
 * Global exception handler for the platform API
 *
 * @author cantaveneraf
 *
 */
@Slf4j
@ControllerAdvice
public class PlatformApiExceptionHandler {

    /**
     * Handles Exceptions caused by a malformed request by returning HTTP status 400
     *
     * @param e       The exception to handle
     * @param request The web request that originated the exception
     * @return The response entity with HTTP status 400
     */
    @ResponseBody
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, UnsupportedOperationException.class,
    })
    public ResponseEntity<Object> handleMalformedRequestExceptions(RuntimeException e, WebRequest request) {
        return ResponseEntity.badRequest().build();
    }


    /**
     * Handles Exceptions caused by a request referencing a platform entity that does not exist by returning
     * HTTP status 404
     *
     * @param e       The exception to handle
     * @param request The web request that originated the exception
     * @return The response entity with HTTP status 404
     */
    @ResponseBody
    @ExceptionHandler({PlatformEntityNotFoundException.class})
    public ResponseEntity<Object> handleEntityNotFoundExceptions(RuntimeException e, WebRequest request) {
        return ResponseEntity.notFound().build();
    }

    /**
     * Handles Exceptions caused by an internal server error by returning HTTP status 500
     *
     * @param e       The exception to handle
     * @param request The web request that originated the exception
     * @return The response entity with HTTP status 500
     */
    @ResponseBody
    @ExceptionHandler({UncheckedIOException.class})
    public ResponseEntity<Object> handleInternalErrorExceptions(RuntimeException e, WebRequest request) {
        return ResponseEntity.internalServerError().build();
    }
}
