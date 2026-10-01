package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Limits;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Requests;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DefaultJobResourceManagementServiceTest {

    private DefaultJobResourceManagementServiceImpl jobResourceManagementService;
    private User platformUser;

    @Before
    public void init() throws Exception {
        platformUser = new User("platform-user");
        jobResourceManagementService = new DefaultJobResourceManagementServiceImpl();
    }

    @Test
    public void testValidateResourceRequest_DoesNoValidation() {
        jobResourceManagementService.validateResourceRequest(platformUser, new PlatformServiceResources());
    }

    @Test
    public void testEvaluateResourceRequest_SetJobResourceRequirementFromServiceResources_WhenServiceResourceRequirementsAreSet() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setStorage("5");
        platformServiceResources.setGpus("10");
        platformServiceResources.setRam("1Gi");
        platformServiceResources.setCpus("0.55");
        JobResourceRequirement jobResourceRequirement = jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources);
        assertThat(jobResourceRequirement).isEqualTo(JobResourceRequirement.builder()
                        .storage(5120)
                        .gpus(10)
                        .requests(Requests.builder().ram("1024Mi").cpu("0.55").build())
                        .limits(Limits.builder().ram("1024Mi").cpu("0.55").build())
                .build());
    }

    @Test
    public void testEvaluateResourceRequest_ThrowsIllegalArgumentException_WhenServiceStorageRequirementValueIsNotValid() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setStorage("5Gi");
        assertThatThrownBy(() -> jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid storage value - must be a positive integer without units: 5Gi");
    }

    @Test
    public void testEvaluateResourceRequest_ThrowsIllegalArgumentException_WhenServiceGPUsRequirementValueIsNotValid() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setGpus("10GPU");
        assertThatThrownBy(() -> jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GPUs value - must be a positive integer without units: 10GPU");
    }

    @Test
    public void testEvaluateResourceRequest_ThrowsIllegalArgumentException_WhenServiceCpuRequirementValueContainsInvalidCaracters() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setCpus("10CDS");
        assertThatThrownBy(() -> jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid CPU value - must be a positive number without units: 10CDS");
    }
    @Test
    public void testEvaluateResourceRequest_ThrowsIllegalArgumentException_WhenServiceCpuRequirementValueIsNegative() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setCpus("-10");
        assertThatThrownBy(() -> jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid CPU value - must be a positive number without units: -10");
    }

    @Test
    public void testEvaluateResourceRequest_SetJobResourceRequirementsToNullValues_WhenServiceResourceRequirementsAreNull() {
        JobResourceRequirement jobResourceRequirement = jobResourceManagementService.evaluateResourceRequest(platformUser, null);
        assertThat(jobResourceRequirement).isEqualTo(JobResourceRequirement.builder()
                .storage(null)
                .gpus(null)
                .requests(Requests.builder().build())
                .limits(Limits.builder().build())
                .build());
    }

    @Test
    public void testEvaluateResourceRequest_SetJobResourceRequirementToNullValues_WhenServiceResourceRequirementsAreBlank() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setStorage("  ");
        platformServiceResources.setGpus("  ");
        platformServiceResources.setRam("  ");
        platformServiceResources.setCpus("  ");
        JobResourceRequirement jobResourceRequirement = jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources);
        assertThat(jobResourceRequirement).isEqualTo(JobResourceRequirement.builder()
                .storage(null)
                .gpus(null)
                .requests(Requests.builder().build())
                .limits(Limits.builder().build())
                .build());
    }

    @Test
    public void testEvaluateResourceRequest_SetJobResourceRequirementToNullValues_WhenServiceResourceRequirementsAreNull() {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setStorage(null);
        platformServiceResources.setGpus(null);
        platformServiceResources.setRam(null);
        platformServiceResources.setCpus(null);
        JobResourceRequirement jobResourceRequirement = jobResourceManagementService.evaluateResourceRequest(platformUser, platformServiceResources);
        assertThat(jobResourceRequirement).isEqualTo(JobResourceRequirement.builder()
                .storage(null)
                .gpus(null)
                .requests(Requests.builder().build())
                .limits(Limits.builder().build())
                .build());
    }
}