package com.cgi.eoss.platform.core.queues;

import com.cgi.eoss.platform.core.queues.service.AMQJmsTemplate;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import java.util.Optional;

/**
 * <p>
 * Configuration of the default implementation of spring beans for the Platform Queues Core module.
 * </p>
 */
@Configuration
public class QueuesCoreDefaultConfig {

    @Bean
    public AMQJmsTemplate nonBlockingJmsTemplate(PooledConnectionFactory pooledConnectionFactory, Optional<MessageConverter> messageConverter) {
        AMQJmsTemplate jmsTemplate = new AMQJmsTemplate(pooledConnectionFactory);
        jmsTemplate.setReceiveTimeout(100);
        messageConverter.ifPresent(jmsTemplate::setMessageConverter);
        return jmsTemplate;
    }

    @Bean
    public AMQJmsTemplate blockingJmsTemplate(PooledConnectionFactory pooledConnectionFactory, Optional<MessageConverter> messageConverter) {
        AMQJmsTemplate jmsTemplate = new AMQJmsTemplate(pooledConnectionFactory);
        jmsTemplate.setReceiveTimeout(JmsTemplate.RECEIVE_TIMEOUT_INDEFINITE_WAIT);
        messageConverter.ifPresent(jmsTemplate::setMessageConverter);
        return jmsTemplate;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(JmsListenerContainerFactoryConfigurer jmsConfigurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        jmsConfigurer.configure(factory);
        return factory;
    }
}
