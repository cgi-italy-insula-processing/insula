package com.cgi.eoss.platform.core.queues.service;

import com.cgi.eoss.platform.core.queues.QueuesCoreConfig;
import com.cgi.eoss.platform.core.queues.QueuesCoreTestConfig;
import com.google.common.collect.ImmutableMap;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_SECOND;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { QueuesCoreConfig.class, QueuesCoreTestConfig.class })
@TestPropertySource("classpath:test-commons-queues.properties")
public class JMSQueueServiceIT {

    private static final String TEST_QUEUE = "test_queue";

    private static final String TEST_QUEUE_1 = "test_queue_1";

    private static final String TEST_QUEUE_2 = "test_queue_2";

    @Autowired
    private QueueService queueService;

    @Before
    public void init() {
        initQueues();
    }

    @Test
    public void testSendWithPriority() {
        String firstMessage = "First message";
        String secondMessage = "Second message";

        queueService.sendObject(TEST_QUEUE, firstMessage, 1);
        queueService.sendObject(TEST_QUEUE, secondMessage, 5);

        String receivedMessage = (String) queueService.receiveObject(TEST_QUEUE);
        assertThat(receivedMessage).isEqualTo(secondMessage);

        receivedMessage = (String) queueService.receiveObject(TEST_QUEUE);
        assertThat(receivedMessage).isEqualTo(firstMessage);

        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testReceiveSelectedObject() {
        String payload1 = "First message";
        {
            Message message = Message.builder()
                        .payload(payload1)
                        .build();
            queueService.send(TEST_QUEUE, message);
        }

        String payload2 = "Second message";
        {
            HashMap<String, Object> headers = new HashMap<>();
            headers.put("jobId", "alphaalpha");
            Message message = Message.builder()
                        .payload(payload2)
                        .headers(headers)
                        .build();
            queueService.send(TEST_QUEUE, message);
        }

        String selector = "jobId = 'alphaalpha'";
        String receivedMessage = (String) queueService.receiveSelectedObject(TEST_QUEUE, selector);
        assertThat(receivedMessage).isEqualTo(payload2);

        assertThat(queueService.receiveSelectedObjectNoWait(TEST_QUEUE, selector)).isNull();

        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(payload1);

        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testReceiveSelectedObjectNoWait() {
        String payload1 = "First message";
        {
            Message message = Message.builder()
                        .payload(payload1)
                        .build();
            queueService.send(TEST_QUEUE, message);
        }

        String payload2 = "Second message";
        {
            HashMap<String, Object> headers = new HashMap<>();
            headers.put("jobId", "alphaalpha");
            Message message = Message.builder()
                        .payload(payload2)
                        .headers(headers)
                        .build();
            queueService.send(TEST_QUEUE, message);
        }

        String selector = "jobId = 'alphaalpha'";

        Awaitility.await().atMost(ONE_SECOND).untilAsserted(() -> assertThat(queueService.getQueueLength(TEST_QUEUE)).isEqualTo(2));

        String receivedMessage = (String) queueService.receiveSelectedObjectNoWait(TEST_QUEUE, selector);
        assertThat(receivedMessage).isEqualTo(payload2);

        assertThat(queueService.receiveSelectedObjectNoWait(TEST_QUEUE, selector)).isNull();

        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(payload1);

        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testReceiveNoWait() {
        String payload = "First message";
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("jobId", "1");
        Message message = Message.builder()
                    .payload(payload)
                    .headers(headers)
                    .build();
        queueService.send(TEST_QUEUE, message);

        Awaitility.await().atMost(ONE_SECOND).untilAsserted(() -> assertThat(queueService.getQueueLength(TEST_QUEUE)).isEqualTo(1));

        Message receivedMessage = queueService.receiveNoWait(TEST_QUEUE);
        assertThat(receivedMessage.getPayload()).isEqualTo(payload);
        assertThat(receivedMessage.getHeaders()).isEqualTo(headers);

        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testReceiveSelectedNoWait() {
        String payload1 = "First message";
        {
            Message message = Message.builder()
                        .payload(payload1)
                        .build();
            queueService.send(TEST_QUEUE, message);
        }

        String payload2 = "Second message";
        HashMap<String, Object> headers2 = new HashMap<>();
        {
            headers2.put("jobId", "alphaalpha");
            Message message = Message.builder()
                        .payload(payload2)
                        .headers(headers2)
                        .build();
            queueService.send(TEST_QUEUE, message);
        }

        String selector = "jobId = 'alphaalpha'";

        Awaitility.await().atMost(ONE_SECOND).untilAsserted(() -> assertThat(queueService.getQueueLength(TEST_QUEUE)).isEqualTo(2));

        Message receivedMessage = queueService.receiveSelectedNoWait(TEST_QUEUE, selector);
        assertThat(receivedMessage.getPayload()).isEqualTo(payload2);
        assertThat(receivedMessage.getHeaders()).isEqualTo(headers2);

        assertThat(queueService.receiveSelectedNoWait(TEST_QUEUE, selector)).isNull();

        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(payload1);
        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testQueueLength() {
        assertThat(queueService.getQueueLength(TEST_QUEUE)).isZero();

        String sentMessage = "Test message";
        queueService.sendObject(TEST_QUEUE, sentMessage);

        assertThat(queueService.getQueueLength(TEST_QUEUE)).isEqualTo(1);

        assertThat(queueService.receiveObject(TEST_QUEUE).toString()).isEqualTo(sentMessage);

        assertThat(queueService.getQueueLength(TEST_QUEUE)).isZero();
    }

    @Test
    public void testSendMessagesWithHeaders() {
        String firstMessage = "First message";
        String secondMessage = "Second message";
        Map<String, Object> headers = ImmutableMap.of("header-1", "value-1", "header-2", "value-2");

        queueService.sendObject(TEST_QUEUE, headers, firstMessage);
        queueService.sendObject(TEST_QUEUE, ImmutableMap.of(), secondMessage);

        Message received_message = queueService.receive(TEST_QUEUE);
        assertThat(received_message.getPayload()).isEqualTo(firstMessage);
        assertThat(received_message.getHeaders()).isEqualTo(headers);

        received_message = queueService.receive(TEST_QUEUE);
        assertThat(received_message.getPayload()).isEqualTo(secondMessage);
        assertThat(received_message.getHeaders()).isEmpty();

        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testSendMessagesWithHeadersAndPriorities() {

        String firstMessage = "First message";
        String secondMessage = "Second message";
        Map<String, Object> headers = ImmutableMap.of("header-1", "value-1", "header-2", "value-2");

        queueService.sendObject(TEST_QUEUE, headers, firstMessage, 1);
        queueService.sendObject(TEST_QUEUE, ImmutableMap.of(), secondMessage, 5);

        Message received_message = queueService.receive(TEST_QUEUE);
        assertThat(received_message.getPayload()).isEqualTo(secondMessage);
        assertThat(received_message.getHeaders()).isEmpty();

        received_message = queueService.receive(TEST_QUEUE);
        assertThat(received_message.getPayload()).isEqualTo(firstMessage);
        assertThat(received_message.getHeaders()).isEqualTo(headers);

        assertThat(queueService.receiveObjectNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testMoveBetweenQueues() {
        HashMap<String, Object> headers1 = new HashMap<>();
        headers1.put("jobId", "400");
        Message message1 = Message.builder().payload("Test message 1").priority(1).headers(headers1).build();
        HashMap<String, Object> headers2 = new HashMap<>();
        headers2.put("jobId", "402");
        Message message2 = Message.builder().payload("Test message 2").priority(1).headers(headers2).build();
        HashMap<String, Object> headers3 = new HashMap<>();
        headers3.put("jobId", "404");
        Message message3 = Message.builder().payload("Test message 3").priority(1).headers(headers3).build();

        queueService.send(TEST_QUEUE_1, message1);
        queueService.send(TEST_QUEUE_1, message2);
        queueService.send(TEST_QUEUE_1, message3);

        assertThat(queueService.getQueueLength(TEST_QUEUE_1)).isEqualTo(3);
        assertThat(queueService.getQueueLength(TEST_QUEUE_2)).isZero();

        List<Message> waitingMessages = new ArrayList<>();

        queueService.browse(TEST_QUEUE_1, new BrowserClient() {

            @Override
            public boolean stopBrowsing() {
                return false;
            }

            @Override
            public void handleMessage(Message m) {
                if (m.getHeaders().get("jobId").equals("402")) {
                    waitingMessages.add(m);
                }

            }
        });

        for (Message waitingMessage : waitingMessages) {
            String jobId = (String) waitingMessage.getHeaders().get("jobId");
            queueService.receiveSelected(TEST_QUEUE_1, "jobId = '" + jobId + "'");
            queueService.send(TEST_QUEUE_2, waitingMessage);
        }

        assertThat(queueService.getQueueLength(TEST_QUEUE_1)).isEqualTo(2);
        assertThat(queueService.getQueueLength(TEST_QUEUE_2)).isEqualTo(1);

        assertThat(queueService.receiveObject(TEST_QUEUE_2).toString()).isEqualTo("Test message 2");
    }

    @Test
    public void testSendDelayedDispatchesMessageNotBeforeTheProvidedDelay() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("jobType", "A");

        String message = "First message";
        queueService.sendObjectDelayed(TEST_QUEUE, headers, message, 1000);

        Awaitility
                    .await()
                    .between(800, TimeUnit.MILLISECONDS, 1500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> assertThat((String) queueService.receiveSelectedObject(TEST_QUEUE, "jobType = 'A'")).isEqualTo(message));

        assertThat(queueService.receiveNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testSendDelayedDispatchesMessageWithoutDelayFirstInsteadOfFifo() {
        String firstMessage = "First message";
        String secondMessage = "Second message";
        Map<String, Object> headers = new HashMap<>();
        queueService.sendObjectDelayed(TEST_QUEUE, headers, firstMessage, 200);
        queueService.sendObject(TEST_QUEUE, secondMessage);

        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(secondMessage);
        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(firstMessage);
        assertThat(queueService.receiveNoWait(TEST_QUEUE)).isNull();
    }

    @Test
    public void testSendDelayedDispatchesMessageWithShorterDelayFirstInsteadOfFifo() {
        String firstMessage = "First message";
        String secondMessage = "Second message";
        Map<String, Object> headers = new HashMap<>();

        queueService.sendObjectDelayed(TEST_QUEUE, headers, firstMessage, 200);
        queueService.sendObjectDelayed(TEST_QUEUE, headers, secondMessage, 100);

        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(secondMessage);
        assertThat(queueService.receiveObject(TEST_QUEUE)).isEqualTo(firstMessage);
        assertThat(queueService.receiveNoWait(TEST_QUEUE)).isNull();

    }

    private void initQueues() {
        initQueue(TEST_QUEUE);
        initQueue(TEST_QUEUE_1);
        initQueue(TEST_QUEUE_2);
    }

    private void initQueue(String queue) {
        queueService.send(queue, Message.builder().payload("").build());
        queueService.receive(queue);
    }
}
