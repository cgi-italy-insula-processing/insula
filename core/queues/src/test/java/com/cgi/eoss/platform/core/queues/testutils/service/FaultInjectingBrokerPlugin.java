package com.cgi.eoss.platform.core.queues.testutils.service;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerFilter;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.command.ActiveMQDestination;

import javax.jms.ResourceAllocationException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An ActiveMQ {@link BrokerPlugin} that rejects messages sends to specific queues.
 * This class is intended for testing only and should not be deployed on production brokers.
 */
public class FaultInjectingBrokerPlugin implements BrokerPlugin {

    private static final Set<String> FAILING_QUEUES = ConcurrentHashMap.newKeySet();

    /**
     * Registers a queue so that all subsequent sends to it are rejected with a
     * {@link ResourceAllocationException}.
     *
     * @param queueName  name of the queue whose sends should fail
     */
    public static void failSendsTo(String queueName) { FAILING_QUEUES.add(queueName); }

    /**
     * Clears all registered failing queues.
     */
    public static void reset() { FAILING_QUEUES.clear(); }

    @Override
    public Broker installPlugin(Broker broker) {
        return new BrokerFilter(broker) {
            @Override
            public void send(ProducerBrokerExchange exchange, org.apache.activemq.command.Message msg) throws Exception {
                ActiveMQDestination destination = msg.getDestination();
                if (destination.isQueue() && FAILING_QUEUES.contains(destination.getPhysicalName())) {
                    throw new ResourceAllocationException("Fault injection: send rejected for " + destination.getPhysicalName());
                }
                super.send(exchange, msg);
            }
        };
    }
}
