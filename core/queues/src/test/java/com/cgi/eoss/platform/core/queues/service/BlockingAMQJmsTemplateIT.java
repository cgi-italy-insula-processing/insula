package com.cgi.eoss.platform.core.queues.service;

import com.cgi.eoss.platform.core.queues.QueuesCoreConfig;
import com.cgi.eoss.platform.core.queues.QueuesCoreTestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.jms.TextMessage;
import java.util.Collections;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { QueuesCoreConfig.class, QueuesCoreTestConfig.class })
@TestPropertySource("classpath:test-commons-queues.properties")
public class BlockingAMQJmsTemplateIT {

    private static final String QUEUE_NAME = "queueName";
    private static final BrowserCallback<Integer> MESSAGE_COUNTER = (session, browser) -> Collections.list((Enumeration<?>) browser.getEnumeration()).size();

    @Autowired
    private AMQJmsTemplate blockingJmsTemplate;

    @Test
    public void testGetReceiveTimeout_ReturnsConfiguredReceiveTimeout() {
        assertThat(blockingJmsTemplate.getReceiveTimeout()).isEqualTo(JmsTemplate.RECEIVE_TIMEOUT_INDEFINITE_WAIT);
    }

    @Test
    public void testReceive_RemovesAndReturnsOneTextMessageFromQueue() throws Exception {
        blockingJmsTemplate.convertAndSend(QUEUE_NAME, "Message", mpp -> {
            mpp.setObjectProperty("header", "value");
            return mpp;
        });

        TextMessage message = (TextMessage) blockingJmsTemplate.receive(QUEUE_NAME);
        assertThat(message.getText()).isEqualTo("Message");
        assertThat(message.getStringProperty("header")).isEqualTo("value");

        assertThat(getQueueSize(blockingJmsTemplate, QUEUE_NAME)).isZero();
    }

    @Test
    public void testReceive_RemovesAndReturnsOneDelayedTextMessageFromQueue_WhenSendDelayIsConfigured() throws Exception {
        blockingJmsTemplate.convertAndSend(QUEUE_NAME, "Message", mpp -> {
            mpp.setLongProperty(QueueConstants.KEY_SEND_DELAY, 1000);
            return mpp;
        });

        Long start = System.nanoTime();
        TextMessage message = (TextMessage) blockingJmsTemplate.receive(QUEUE_NAME);
        Long end = System.nanoTime();
        assertThat(message.getText()).isEqualTo("Message");

        assertThat(java.time.Duration.ofNanos(end - start).toMillis()).isBetween(800L, 3500L);

        assertThat(getQueueSize(blockingJmsTemplate, QUEUE_NAME)).isZero();
    }

    @Test
    public void testReceiveAndConvert_RemovesAndReturnsOneTextMessageConvertedToStringFromQueue() throws Exception {

        blockingJmsTemplate.convertAndSend(QUEUE_NAME, "Message", mpp -> mpp);

        String message = (String) blockingJmsTemplate.receiveAndConvert(QUEUE_NAME);
        assertThat(message).isEqualTo("Message");

        assertThat(getQueueSize(blockingJmsTemplate, QUEUE_NAME)).isZero();
    }

    private static int getQueueSize(JmsTemplate jmsTemplate, String queueName) {
        Integer size = jmsTemplate.browse(queueName, MESSAGE_COUNTER);
        return size == null ? 0 : size;
    }
}
