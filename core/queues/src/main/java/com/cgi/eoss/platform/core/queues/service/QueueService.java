package com.cgi.eoss.platform.core.queues.service;

import java.util.Map;

public interface QueueService {

    void sendObject(String queueName, Object object);

    void sendObject(String queueName, Object object, int priority);

    void sendObject(String queueName, Map<String, Object> additionalHeaders, Object object);

    void sendObject(String queueName, Map<String, Object> additionalHeaders, Object object, int priority);

    void sendObjectDelayed(String queueName, Map<String, Object> additionalHeaders, Object object, long delay);

    void send(String queueName, Message message);

    Object receiveObject(String queueName);

    Object receiveObjectNoWait(String queueName);

    Object receiveSelectedObject(String queueName, String messageSelector);

    Object receiveSelectedObjectNoWait(String queueName, String messageSelector);

    long getQueueLength(String queueName);

    Message receiveSelected(String queueName, String messageSelector);

    Message receive(String queueName);

    Message receiveNoWait(String queueName);

    Message receiveSelectedNoWait(String queueName, String messageSelector);

    void browse(String queueName, BrowserClient browserClient);

}
