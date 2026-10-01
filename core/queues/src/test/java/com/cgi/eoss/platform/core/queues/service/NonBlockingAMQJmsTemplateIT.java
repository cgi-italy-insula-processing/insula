package com.cgi.eoss.platform.core.queues.service;

import com.cgi.eoss.platform.core.queues.QueuesCoreConfig;
import com.cgi.eoss.platform.core.queues.QueuesCoreTestConfig;
import org.awaitility.Awaitility;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_SECOND;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { QueuesCoreConfig.class, QueuesCoreTestConfig.class })
@TestPropertySource("classpath:test-commons-queues.properties")
public class NonBlockingAMQJmsTemplateIT {

    private static final String QUEUE_NAME = "queueName";
    private static final BrowserCallback<Integer> MESSAGE_COUNTER = (session, browser) -> Collections.list((Enumeration<?>) browser.getEnumeration()).size();

    @Autowired
    private AMQJmsTemplate nonBlockingJmsTemplate;

    @Test
    public void testGetReceiveTimeout_ReturnsConfiguredReceiveTimeout() {
        assertThat(nonBlockingJmsTemplate.getReceiveTimeout()).isEqualTo(100);
    }

    @Test
    public void testReceive_EventuallyRemovesAndReturnsOneTextMessageFromQueue() throws Exception {
        nonBlockingJmsTemplate.convertAndSend(QUEUE_NAME, "Message", mpp -> {
            mpp.setObjectProperty("header", "value");
            return mpp;
        });

        Awaitility.await().atMost(ONE_SECOND)
                .untilAsserted(() -> {
                    assertThat(getQueueSize(nonBlockingJmsTemplate, QUEUE_NAME)).isEqualTo(1);
                });

        TextMessage message = (TextMessage) nonBlockingJmsTemplate.receive(QUEUE_NAME);
        assertThat(message.getText()).isEqualTo("Message");
        assertThat(message.getStringProperty("header")).isEqualTo("value");

        assertThat(nonBlockingJmsTemplate.receiveAndConvert(QUEUE_NAME)).isNull();
    }

    @Test
    public void testReceive_EventuallyRemovesAndReturnsOneDelayedTextMessageFromQueue_WhenSendDelayIsConfigured() throws Exception {
        nonBlockingJmsTemplate.convertAndSend(QUEUE_NAME, "Message", mpp -> {
            mpp.setLongProperty(QueueConstants.KEY_SEND_DELAY, 1000);
            return mpp;
        });

        Awaitility.await()
                .between(800L, TimeUnit.MILLISECONDS, 3500L, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(getQueueSize(nonBlockingJmsTemplate, QUEUE_NAME)).isEqualTo(1);
                });

        TextMessage message = (TextMessage) nonBlockingJmsTemplate.receive(QUEUE_NAME);
        assertThat(message.getText()).isEqualTo("Message");

        assertThat(getQueueSize(nonBlockingJmsTemplate, QUEUE_NAME)).isZero();
    }

    @Test
    public void testReceiveAndConvert_EventuallyRemovesAndReturnsOneTextMessageConvertedToStringFromQueue() throws Exception {

        nonBlockingJmsTemplate.convertAndSend(QUEUE_NAME, "Message", mpp -> mpp);

        Awaitility.await().atMost(ONE_SECOND)
                .untilAsserted(() -> {
                    assertThat(getQueueSize(nonBlockingJmsTemplate, QUEUE_NAME)).isEqualTo(1);
                });

        String message = (String) nonBlockingJmsTemplate.receiveAndConvert(QUEUE_NAME);
        assertThat(message).isEqualTo("Message");

        assertThat(nonBlockingJmsTemplate.receiveAndConvert(QUEUE_NAME)).isNull();
    }

    private static int getQueueSize(JmsTemplate jmsTemplate, String queueName) {
        Integer size = jmsTemplate.browse(queueName, MESSAGE_COUNTER);
        return size == null ? 0 : size;
    }
}
