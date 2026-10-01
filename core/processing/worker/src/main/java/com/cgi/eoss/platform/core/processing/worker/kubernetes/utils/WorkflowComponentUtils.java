package com.cgi.eoss.platform.core.processing.worker.kubernetes.utils;

import com.cgi.eoss.platform.rpc.SharedMemory;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Container;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Spec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Volume;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeEmptyDir;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeMount;
import com.google.common.base.Strings;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;

/**
 * Utility class for configuring workflow components.
 *
 */
public class WorkflowComponentUtils {

    private static final String SHARED_MEMORY_PVC_PATH = "/dev/shm";
    private static final String SHARED_MEMORY_PVC_NAME = "devshm";
    private static final String SHARED_MEMORY_TYPE = "Memory";

    /**
     * Adds a shared memory volume (<code>emptyDir</code> with memory medium) to the given
     * Kubernetes {@link V1PodSpec} and mounts it into the provided {@link V1Container} at
     * <code>/dev/shm</code>.
     * @param sharedMemory  the shared memory configuration, specifying the size (e.g., "64Mi")
     * @param spec          the Kubernetes pod spec to which the volume should be added
     * @param userContainer the container that requires the shared memory mount
     */
    public static void addSharedMemory(SharedMemory sharedMemory, V1PodSpec spec, V1Container userContainer) {

        String sharedMemorySize = sharedMemory.getSize();
        if (Strings.isNullOrEmpty(sharedMemorySize)) {

            return;
        }

        spec.addVolumesItem(
                new V1Volume()
                        .name(SHARED_MEMORY_PVC_NAME)
                        .emptyDir(
                                new V1EmptyDirVolumeSource()
                                        .medium(SHARED_MEMORY_TYPE)
                                        .sizeLimit(new Quantity(sharedMemorySize))
                        )
        );

        userContainer.addVolumeMountsItem(
                new V1VolumeMount()
                        .name(SHARED_MEMORY_PVC_NAME)
                        .mountPath(SHARED_MEMORY_PVC_PATH)
        );
    }
    /**
     * Adds a shared memory volume to the given workflow specification and container definition.

     * @param sharedMemory the shared memory configuration containing the size limit (e.g. "512Mi");
     *                     if null or empty, no volume will be added
     * @param spec         the workflow {@link Spec} object where the volume should be added
     * @param container    the container definition where the volume mount should be added
     */
    public static void addSharedMemory(SharedMemory sharedMemory, Spec spec, Container container){

        String sharedMemorySize = sharedMemory.getSize();
        if (Strings.isNullOrEmpty(sharedMemorySize)) {

            return;
        }

        VolumeEmptyDir emptyDir = new VolumeEmptyDir();
        emptyDir.setSizeLimit(sharedMemorySize);
        emptyDir.setMedium(SHARED_MEMORY_TYPE);

        Volume volume = new Volume();
        volume.setEmptyDir(emptyDir);
        volume.setName(SHARED_MEMORY_PVC_NAME);

        VolumeMount volumeMount = new VolumeMount();
        volumeMount.setMountPath(SHARED_MEMORY_PVC_PATH);
        volumeMount.setName(SHARED_MEMORY_PVC_NAME);

        spec.getVolumes()
                .add(volume);

        container.getVolumeMounts().add(volumeMount);
    }

}
