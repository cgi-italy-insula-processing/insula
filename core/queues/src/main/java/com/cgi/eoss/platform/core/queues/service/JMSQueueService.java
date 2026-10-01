package com.cgi.eoss.platform.core.queues.service;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.jms.support.converter.MessageConversionException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class JMSQueueService implements QueueService {

    private final JmsTemplate blockingJmsTemplate;
    private final JmsTemplate nonBlockingJmsTemplate;

    @Autowired
    public JMSQueueService(JmsTemplate blockingJmsTemplate, JmsTemplate nonBlockingJmsTemplate) {
        this.blockingJmsTemplate = blockingJmsTemplate;
        this.nonBlockingJmsTemplate = nonBlockingJmsTemplate;
    }

    @Override
    public void sendObject(String queueName, Object object) {
        send(queueName, Message.builder().payload(object).build());
    }

    @Override
    public void sendObject(String queueName, Object object, int priority) {
        send(queueName, Message.builder().payload(object).priority(priority).build());
    }

    @Override
    public void sendObject(String queueName, Map<String, Object> additionalHeaders, Object object) {
        send(queueName, Message.builder().payload(object).headers(additionalHeaders).build());
    }

    @Override
    public void sendObjectDelayed(String queueName, Map<String, Object> additionalHeaders, Object object, long delay) {
        send(queueName, Message.builder().payload(object).headers(additionalHeaders).delay(delay).build());
    }

    @Override
    public void sendObject(String queueName, Map<String, Object> additionalHeaders, Object object, int priority) {
        send(queueName, Message.builder().payload(object).headers(additionalHeaders).priority(priority).build());
    }

    @Override
    public void send(String queueName, Message message) {
        Object payload = message.getPayload();
        Integer priority = message.getPriority();
        Long delay = message.getDelay();

        LOG.debug("Sending message {} of type {} to queue {}", payload, payload.getClass().getCanonicalName(), queueName);

        blockingJmsTemplate.convertAndSend(queueName, payload, (MessagePostProcessor) jmsMsg -> {
            if (priority != null) {
                jmsMsg.setJMSPriority(priority);
            }

            message.getHeaders().forEach((k, v) -> {
                try {
                    jmsMsg.setObjectProperty(k, v);
                } catch (JMSException e) {
                    LOG.error("Error sending message to JMS Queue " + queueName, e);
                }
            });

            if (delay != null) {
                jmsMsg.setLongProperty(QueueConstants.KEY_SEND_DELAY, delay);
            }

            return jmsMsg;
        });
    }

    @Override
    public Object receiveObject(String queueName) {
        return blockingJmsTemplate.receiveAndConvert(queueName);
    }

    @Override
    public Object receiveObjectNoWait(String queueName) {
        return nonBlockingJmsTemplate.receiveAndConvert(queueName);
    }

    @Override
    public Object receiveSelectedObject(String queueName, String messageSelector) {
        return blockingJmsTemplate.receiveSelectedAndConvert(queueName, messageSelector);
    }

    @Override
    public Object receiveSelectedObjectNoWait(String queueName, String messageSelector) {
        return nonBlockingJmsTemplate.receiveSelectedAndConvert(queueName, messageSelector);
    }

    @Override
    public Message receive(String queueName) {
        String selector = null;
        return receiveSelected(queueName, selector);
    }

    @Override
    public Message receiveSelected(String queueName, String messageSelector) {
        javax.jms.Message jmsMessage = blockingJmsTemplate.receiveSelected(queueName, messageSelector);
        return fromJMSMessageSafe(jmsMessage);
    }

    @Override
    public Message receiveNoWait(String queueName) {
        String selector = null;
        return receiveSelectedNoWait(queueName, selector);
    }

    @Override
    public Message receiveSelectedNoWait(String queueName, String selector) {
        javax.jms.Message jmsMessage = nonBlockingJmsTemplate.receiveSelected(queueName, selector);
        if (jmsMessage == null) {
            return null;
        }
        return fromJMSMessageSafe(jmsMessage);
    }

    @Override
    public long getQueueLength(String queueName) {
        return blockingJmsTemplate.execute(session -> {
            return getQueueSize(session, queueName);
        }, true);
    }

    @Override
    public void browse(String queueName, BrowserClient browserClient) {
        this.blockingJmsTemplate.browse(queueName, (BrowserCallback<Void>) (session, browser) -> {
            try {
                Enumeration<?> enumeration = browser.getEnumeration();
                while (enumeration.hasMoreElements() && browserClient.stopBrowsing() == false) {
                    javax.jms.Message msg = (javax.jms.Message) enumeration.nextElement();
                    browserClient.handleMessage(fromJMSMessage(msg));
                }
                browser.close();
            } catch (JMSException e) {
                throw new UncategorizedJmsException(e);
            }

            return null;
        });
    }

    private Message fromJMSMessageSafe(javax.jms.Message jmsMessage) {
        try {
            return fromJMSMessage(jmsMessage);
        } catch (MessageConversionException | JMSException e) {
            throw new MessageConversionException(e.getMessage());
        }
    }

    private Message fromJMSMessage(javax.jms.Message jmsMessage) throws JMSException {
        Object payload = blockingJmsTemplate.getMessageConverter().fromMessage(jmsMessage);
        Map<String, Object> headers = new HashMap<>();
        Enumeration<?> e = jmsMessage.getPropertyNames();
        while (e.hasMoreElements()) {
            String propertyName = (String) e.nextElement();
            headers.put(propertyName, jmsMessage.getObjectProperty(propertyName));
        }
        return Message.builder()
                    .payload(payload)
                    .priority(jmsMessage.getJMSPriority())
                    .headers(headers)
                    .build();
    }

    private static Long getQueueSize(Session session, String queueName) throws JMSException {
        String statisticsQueueName = "ActiveMQ.Statistics.Destination." + queueName;
        Queue statisticsQueue = session.createQueue(statisticsQueueName);
        Queue replyTo = session.createTemporaryQueue();
        try (MessageConsumer consumer = session.createConsumer(replyTo);
             MessageProducer producer = session.createProducer(statisticsQueue)) {
            javax.jms.Message msg = session.createMessage();
            msg.setJMSReplyTo(replyTo);
            producer.send(msg);
            MapMessage reply = (MapMessage) consumer.receive();
            return reply.getLong("size");
        }
    }
}
