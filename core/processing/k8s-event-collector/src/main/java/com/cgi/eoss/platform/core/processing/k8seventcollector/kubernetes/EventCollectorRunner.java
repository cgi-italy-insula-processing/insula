package com.cgi.eoss.platform.core.processing.k8seventcollector.kubernetes;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;

@Log4j2
@RequiredArgsConstructor
public class EventCollectorRunner implements CommandLineRunner {

    private final KubernetesEventCollectorManager kubernetesEventCollectorManager;

    /**
     * Starts the {@link KubernetesEventCollectorManager} bean.
     */
    @Override
    public void run(String... args) throws Exception {
        LOG.info("Start of kubernetes event collection");
        kubernetesEventCollectorManager.start();
    }

}
