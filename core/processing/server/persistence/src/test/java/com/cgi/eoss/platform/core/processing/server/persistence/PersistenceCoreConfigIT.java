package com.cgi.eoss.platform.core.processing.server.persistence;

import com.cgi.eoss.platform.core.processing.server.persistence.service.ProcessingCoreDataInitializationManagedService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ProcessingCoreDataInitializationService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class PersistenceCoreConfigIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testContextLoads() {
        assertThat(applicationContext.getBean(ProcessingCoreDataInitializationManagedService.class)).isNotNull();
        assertThat(applicationContext.getBean(ProcessingCoreDataInitializationService.class)).isNotNull();
    }

}