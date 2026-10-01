package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@Import({AppCoreConfig.class})
@ConditionalOnExpression(
        "'${platform.core.processing.k8seventcollector.autorun.enabled}' == 'true' " +
                "and '${platform.k8seventcollector.app}' == 'processing-core'"
)
public class KubernetesEventCollectorCoreApplication {

    public static void main(String[] args) {
        LOG.info("Starting kubernetes event collector application");
        SpringApplication.run(KubernetesEventCollectorCoreApplication.class, args);
    }
}
