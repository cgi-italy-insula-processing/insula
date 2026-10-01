package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.orchestrator.utils.CoreModelToGrpcUtils;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserMountDataService;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Resolves a service's additional mounts into the gRPC {@link com.cgi.eoss.platform.rpc.UserMount} representations.
 *
 */
@AllArgsConstructor
public class UserMountResolver {

    private final UserMountDataService userMountDataService;

    /**
     * Resolves a service's additional mounts into the gRPC user mount representations.
     *
     * @param service the service whose additional mounts are resolved
     * @return a List populated with the resolved gRPC {@link com.cgi.eoss.platform.rpc.UserMount}s if the service has
     *         additional mounts, an empty List otherwise
     * @throws PlatformEntityNotFoundException if an additional mount references a user mount that cannot be loaded
     */
    public List<com.cgi.eoss.platform.rpc.UserMount> resolve(PlatformService service) {
        return service.getAdditionalMounts().entrySet().stream()
                .map(this::resolveUserMount)
                .collect(Collectors.toList());
    }

    private com.cgi.eoss.platform.rpc.UserMount resolveUserMount(Entry<Long, String> entry) {
        return CoreModelToGrpcUtils.toRpcUserMount(
                userMountDataService.getById(entry.getKey())
                        .orElseThrow(() -> new PlatformEntityNotFoundException("Failed to load User Mount with ID: " + entry.getKey())),
                entry.getValue());
    }

}
