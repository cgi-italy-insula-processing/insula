package com.cgi.eoss.platform.core.queues;

import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/**
 * Interface to configure DefaultJmsListenerContainerFactory
 *
 * @author cantaveneraf
 *
 */
public interface JmsListenerContainerFactoryConfigurer {

    /**
     * Configure the provided listenerFactory
     *
     * @param listenerFactory
     *            The JMS listener container factory to configure
     * 
     */
    void configure(DefaultJmsListenerContainerFactory listenerFactory);

}
