package com.cgi.eoss.platform.core.queues.service;

import java.util.HashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Message {

    private Object payload;

    private Integer priority;

    private Long delay;

    @Builder.Default
    private Map<String, Object> headers = new HashMap<>();

}
