package com.cgi.eoss.platform.core.queues.service;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.apache.activemq.ScheduledMessage;
import org.springframework.jms.core.JmsTemplate;

/**
 * Spring JMS Template extension that supports ActiveMQ broker features.
 *
 * @author cantaveneraf
 *
 */
public class AMQJmsTemplate extends JmsTemplate {

    /**
     * Initialize an instance of this class with the provided JMS Connection Factory
     *
     * @param connectionFactory
     *            The JMS Connection Factory
     */
    public AMQJmsTemplate(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    @Override
    protected void doSend(MessageProducer producer, Message message) throws JMSException {
        if (message.propertyExists(QueueConstants.KEY_SEND_DELAY)) {
            message.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, message.getLongProperty(QueueConstants.KEY_SEND_DELAY));
        }
        producer.send(message, getDeliveryMode(), message.getJMSPriority(), getTimeToLive());
    }
}
