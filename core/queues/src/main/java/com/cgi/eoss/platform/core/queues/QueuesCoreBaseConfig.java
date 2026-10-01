package com.cgi.eoss.platform.core.queues;

import com.cgi.eoss.platform.core.queues.service.JMSQueueService;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Arrays;
import java.util.Optional;

/**
 * <p>
 * Configuration of the spring beans for the Platform Queues Core module.
 * </p>
 */
@Configuration
@EnableJms
@Import({ PropertyPlaceholderAutoConfiguration.class })
public class QueuesCoreBaseConfig {

    @Bean(name = "activeMQConnectionFactory")
    public ActiveMQConnectionFactory activeMQConnectionFactory(
            @Value("${spring.activemq.broker-url}") String brokerUrl,
            @Value("${spring.activemq.user:admin}") String brokerUserName,
            @Value("${spring.activemq.password:admin}") String brokerPassword,
            @Value("${platform.queues.activemq.messages.trustedPackages:com.google.protobuf,com.cgi.eoss.platform.rpc}") String[] trustedActiveMQPackages
    ) {
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
        activeMQConnectionFactory.setUserName(brokerUserName);
        activeMQConnectionFactory.setPassword(brokerPassword);
        activeMQConnectionFactory.setTrustedPackages(Arrays.asList(trustedActiveMQPackages));
        activeMQConnectionFactory.setBrokerURL(brokerUrl);

        return activeMQConnectionFactory;
    }

    @Bean
    public JmsListenerContainerFactoryConfigurer jmsConfigurer(ActiveMQConnectionFactory activeMQConnectionFactory,
                                                               Optional<MessageConverter> messageConverter,
                                                               Optional<FixedBackOff> jmsConfigurerBackOff) {
        return listenerFactory -> {
            listenerFactory.setConnectionFactory(activeMQConnectionFactory);
            listenerFactory.setConcurrency("1-1");
            jmsConfigurerBackOff.ifPresent(listenerFactory::setBackOff);
            messageConverter.ifPresent(listenerFactory::setMessageConverter);
        };
    }

    @Bean(destroyMethod = "stop")
    public PooledConnectionFactory pooledConnectionFactory(ActiveMQConnectionFactory activeMQConnectionFactory) {
        return new PooledConnectionFactory(activeMQConnectionFactory);
    }

    @Bean("queueService")
    public JMSQueueService queueService(JmsTemplate blockingJmsTemplate, JmsTemplate nonBlockingJmsTemplate) {
        return new JMSQueueService(blockingJmsTemplate, nonBlockingJmsTemplate);
    }
}
