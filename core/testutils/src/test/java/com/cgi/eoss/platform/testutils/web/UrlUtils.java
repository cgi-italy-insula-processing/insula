package com.cgi.eoss.platform.testutils.web;

import java.net.URI;
import java.net.URISyntaxException;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String getLastPathSegment(String uri) throws URISyntaxException {
        return getLastPathSegment(new URI(uri));
    }

    public static String getLastPathSegment(URI uri) {
        String[] segments = uri.getPath().split("/");
        return segments[segments.length - 1];
    }
}
