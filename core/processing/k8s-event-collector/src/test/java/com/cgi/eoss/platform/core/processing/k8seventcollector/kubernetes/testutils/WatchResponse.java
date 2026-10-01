package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes.testutils;

import lombok.Data;

@Data
public class WatchResponse<T> {
    private String type;
    private T object;
}
