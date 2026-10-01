package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.JobResourceRequirement;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Limits;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.Requests;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.model.ResourceBounds;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util.ResourceUtils.getServiceGPUs;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util.ResourceUtils.getServiceRamInMb;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util.ResourceUtils.getServiceStorageInMb;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util.ResourceUtils.getServiceCpuInMillis;

/**
 * Service class to set Job resource requirements based on services resource requirements.
 */
@Log4j2
@AllArgsConstructor
public class DefaultJobResourceManagementServiceImpl implements JobResourceManagementService {

    private static final long MB_IN_GB = 1024L;
    private static final double CPU_MILLI_FACTOR = 1000.0;

    @Override
    public void validateResourceRequest(User user, PlatformServiceResources platformServiceResources) {
        // doNothing for this method.
    }

    @Override
    public JobResourceRequirement evaluateResourceRequest(User user, PlatformServiceResources platformServiceResources) {
        Integer storageMb = evaluateStorage(platformServiceResources);
        Integer gpus = evaluateGPU(platformServiceResources);
        ResourceBounds ram = evaluateRam(platformServiceResources);
        ResourceBounds cpu = evaluateCpu(platformServiceResources);

        return JobResourceRequirement.builder()
                .storage(storageMb)
                .gpus(gpus)
                .requests(Requests.builder().ram(ram.getRequest()).cpu(cpu.getRequest()).build())
                .limits(Limits.builder().ram(ram.getLimit()).cpu(cpu.getLimit()).build())
                .build();
    }

    private Integer evaluateStorage(PlatformServiceResources platformServiceResources) {
        return getServiceStorageInMb(platformServiceResources);
    }

    private Integer evaluateGPU(PlatformServiceResources platformServiceResources) {
        return getServiceGPUs(platformServiceResources);
    }

    private ResourceBounds evaluateRam(PlatformServiceResources platformServiceResources) {

        Integer ramInMb = getServiceRamInMb(platformServiceResources);
        if (ramInMb == null) {
            return ResourceBounds.builder().build();
        }

        return ResourceBounds.builder()
                .request(ramInMb + "Mi")
                .limit(ramInMb + "Mi")
                .build();
    }

    private ResourceBounds evaluateCpu(PlatformServiceResources platformServiceResources) {
        Integer serviceCpuMillis = getServiceCpuInMillis(platformServiceResources);
        if (serviceCpuMillis == null) {
            return ResourceBounds.builder().build();
        }

        String serviceCpu = String.valueOf(serviceCpuMillis / CPU_MILLI_FACTOR);

        return ResourceBounds.builder()
                .request(serviceCpu)
                .limit(serviceCpu)
                .build();
    }
}