package com.cgi.eoss.platform.core.processing.server.orchestrator.utils;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.Service;
import com.cgi.eoss.platform.rpc.UserMount;

/**
 * Utility class providing helper methods to map processing model objects to their gRPC representations.
 */
public class CoreModelToGrpcUtils {

    private static final String DESCRIPTOR_TYPE_CWL = "CWL";

    protected CoreModelToGrpcUtils() {
    }

    /**
     * Convert a {@link com.cgi.eoss.platform.core.processing.server.model.Job} to its gRPC {@link Job} representation.
     * @param job   The job to be converted.
     * @return  The input job mapped to {@link Job}.
     */
    public static Job toRpcJob(com.cgi.eoss.platform.core.processing.server.model.Job job) {
        Job.Builder builder = Job.newBuilder()
                .setId(job.getExtId())
                .setIntJobId(String.valueOf(job.getId()))
                .setUserId(job.getOwner().getName())
                .setServiceId(job.getConfig().getService().getName());
        String uuid = job.getOwner().getUuid();
        if (uuid != null) {
            builder.setUserUUID(uuid);
        }
        return builder.build();
    }

    /**
     * Convert a {@link PlatformService} to its gRPC {@link Service} representation.
     * @param service   The service to be converted.
     * @return  The input service mapped to {@link Service}.
     */
    public static Service toRpcService(PlatformService service) {
        return Service.newBuilder()
                .setId(String.valueOf(service.getId()))
                .setName(service.getName())
                .setDockerImageTag(service.getDockerTag())
                .setGroupId(service.getGroupId() != null ? service.getGroupId() : PlatformService.DEFAULT_GROUP_ID)
                .setDescriptorType(service.getCwl() != null ? DESCRIPTOR_TYPE_CWL : "")
                .build();
    }

    /**
     * Convert a {@link com.cgi.eoss.platform.core.processing.server.model.UserMount} to its gRPC {@link UserMount}
     * representation.
     *
     * @param userMount     The userMount to be converted.
     * @param targetPath    The path where resources are mounted.
     * @return  The input userMount mapped to {@link UserMount}.
     */
    public static UserMount toRpcUserMount(com.cgi.eoss.platform.core.processing.server.model.UserMount userMount, String targetPath) {
        UserMount.Builder builder = UserMount.newBuilder()
                .setName(userMount.getName())
                .setMountPath(userMount.getMountPath())
                .setType(userMount.getType().toString().toLowerCase())
                .setTargetPath(targetPath);
        return builder.build();
    }
}