package com.cgi.eoss.platform.testutils.web;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import java.util.Base64;

import okhttp3.mockwebserver.RecordedRequest;

/**
 * Verifier of HTTP Authorization header
 *
 */
public class AuthHeaderVerifier {

    private final String authHeader;

    /**
     * Create an instance of Basic Authorization Header verifier for the provided credentials
     *
     * @param username
     *            The username to encode within the Basic Authorization Header
     * @param password
     *            The password to encode within the Basic Authorization Header
     * @return
     *         A basic Authorization Header verifier instance
     */
    public static AuthHeaderVerifier basic(String username, String password) {
        return new AuthHeaderVerifier("Basic", username, password);
    }

    /**
     * Verifies that the provided recordedRequest contains the Authorization Header
     * wrapped by this instance. Throws an Error if this is not the case.
     *
     * @param recordedRequest
     *            The HTTP Request that contains the Authorization Header to verify
     */
    public void verify(RecordedRequest recordedRequest) {
        assertThat(recordedRequest.getHeader("Authorization"), is(authHeader));
    }

    private AuthHeaderVerifier(String authType, String username, String password) {
        authHeader = authType + " " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    private AuthHeaderVerifier(String authType, String authToken) {
        authHeader = authType + " " + Base64.getEncoder().encodeToString((authToken).getBytes());
    }

    public static AuthHeaderVerifier bearer(String authToken) {
        return new AuthHeaderVerifier("Bearer", authToken);
    }
}
