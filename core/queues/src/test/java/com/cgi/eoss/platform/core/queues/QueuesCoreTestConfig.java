package com.cgi.eoss.platform.core.queues;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cgi.eoss.platform.core.queues.testutils.service.FaultInjectingBrokerPlugin;
import lombok.extern.log4j.Log4j2;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.broker.scheduler.memory.InMemoryJobSchedulerStore;
import org.apache.activemq.plugin.StatisticsBrokerPlugin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@TestConfiguration
public class QueuesCoreTestConfig {

    @Bean(destroyMethod = "stop")
    public BrokerService brokerService(@Value("${platform.core.queues.broker.name}") String brokerName) {
        BrokerService broker = new BrokerService();
        broker.setBrokerName(brokerName);

        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setAdvisorySupport(false);
        broker.setSchedulerSupport(true);
        broker.setJobSchedulerStore(new InMemoryJobSchedulerStore());

        broker.setPlugins(new BrokerPlugin[] {
                new StatisticsBrokerPlugin(),
                new FaultInjectingBrokerPlugin()
        });

        PolicyEntry policyEntry = new PolicyEntry();
        policyEntry.setPrioritizedMessages(true);
        PolicyMap policyMap = new PolicyMap();
        policyMap.setDefaultEntry(policyEntry);
        broker.setDestinationPolicy(policyMap);

        broker.setUseShutdownHook(false);

        return broker;
    }

    @Bean
    public static BeanFactoryPostProcessor embeddedBrokerProperties(ConfigurableEnvironment env) {
        String brokerName = "embeddedBroker-" + UUID.randomUUID();
        Map<String, Object> props = new HashMap<>();
        props.put("platform.core.queues.broker.name", brokerName);
        props.put("spring.activemq.broker-url", "vm://" + brokerName + "?create=false&waitForStart=10000");
        env.getPropertySources().addFirst(new MapPropertySource("embeddedBrokerProps", props));
        return beanFactory -> { /* work already done above */ };
    }

    @Bean
    public FixedBackOff jmsConfigurerBackOff() {
        return new FixedBackOff(500, 3);
    }
}
