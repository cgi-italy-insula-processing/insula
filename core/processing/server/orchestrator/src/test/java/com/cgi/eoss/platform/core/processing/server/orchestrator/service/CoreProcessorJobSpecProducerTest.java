package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CoreProcessorJobSpecProducerTest {

    private final User owner = new User("platform-test-user");

    private CoreProcessorJobSpecProducer coreProcessorJobSpecProducer;

    @Before
    public void setUp() {
        coreProcessorJobSpecProducer = new CoreProcessorJobSpecProducer(mock(JobDataService.class),
                mock(UserMountResolver.class));
    }

    @Test
    public void testSupports_ReturnsTrue_WhenServiceTypeIsAnyProcessorType() {
        assertThat(coreProcessorJobSpecProducer.supports(createServiceOfType(PlatformService.Type.PROCESSOR))).isTrue();
        assertThat(coreProcessorJobSpecProducer.supports(createServiceOfType(PlatformService.Type.PARALLEL_PROCESSOR))).isTrue();
        assertThat(coreProcessorJobSpecProducer.supports(createServiceOfType(PlatformService.Type.BULK_PROCESSOR))).isTrue();
    }

    @Test
    public void testSupports_ReturnsFalse_WhenServiceTypeIsApplication() {
        assertThat(coreProcessorJobSpecProducer.supports(createServiceOfType(PlatformService.Type.APPLICATION))).isFalse();
    }

    private PlatformService createServiceOfType(PlatformService.Type type) {
        PlatformService service = new PlatformService("serviceName", owner, "dockerTag");
        service.setType(type);
        return service;
    }
}
