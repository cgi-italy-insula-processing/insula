package com.cgi.eoss.platform.core.processing.worker.kubernetes.utils;


import com.cgi.eoss.platform.rpc.SharedMemory;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Container;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Spec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Volume;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeMount;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowComponentUtilsTest {

    private V1PodSpec podSpec;
    private V1Container container;

    @Before
    public void setUp() {
        podSpec = new V1PodSpec();
        container = new V1Container();
    }

    @Test
    public void testAddSharedMemory_DoesNothing_WhenSharedMemoryHasNullSize() {
        SharedMemory sharedMemory = SharedMemory.newBuilder().build();

        WorkflowComponentUtils.addSharedMemory(sharedMemory, podSpec, container);

        assertThat(podSpec.getVolumes()).isNull();
        assertThat(container.getVolumeMounts()).isNull();
    }

    @Test
    public void testAddSharedMemory_DoesNothing_WhenShareMemoryHasEmptySize() {
        SharedMemory sharedMemory = SharedMemory.newBuilder().setSize("").build();

        WorkflowComponentUtils.addSharedMemory(sharedMemory, podSpec, container);

        assertThat(podSpec.getVolumes()).isNull();
        assertThat(container.getVolumeMounts()).isNull();
    }

    @Test
    public void testAddSharedMemory_AddsVolumeAndMount_WhenHasValidSize() {
        SharedMemory sharedMemory = SharedMemory.newBuilder().setSize("128Mi").build();

        WorkflowComponentUtils.addSharedMemory(sharedMemory, podSpec, container);

        assertThat(podSpec.getVolumes()).hasSize(1);
        V1Volume volume = podSpec.getVolumes().get(0);
        assertThat(volume.getName()).isEqualTo("devshm");
        assertThat(volume.getEmptyDir()).isNotNull();
        assertThat(volume.getEmptyDir().getMedium()).isEqualTo("Memory");
        assertThat(volume.getEmptyDir().getSizeLimit().toSuffixedString()).isEqualTo("128Mi");

        assertThat(container.getVolumeMounts()).hasSize(1);
        V1VolumeMount mount = container.getVolumeMounts().get(0);
        assertThat(mount.getName()).isEqualTo("devshm");
        assertThat(mount.getMountPath()).isEqualTo("/dev/shm");
    }

    @Test
    public void testAddSharedMemory_AppendsToExistingVolumesAndMounts_WhenContainerContainsMountAndVolume() {

        V1Volume existingVolume = new V1Volume().name("existing-volume");
        V1VolumeMount existingMount = new V1VolumeMount()
                .name("existing-mount")
                .mountPath("/existing");

        podSpec.addVolumesItem(existingVolume);
        container.addVolumeMountsItem(existingMount);

        SharedMemory sharedMemory = SharedMemory.newBuilder().setSize("64Mi").build();

        WorkflowComponentUtils.addSharedMemory(sharedMemory, podSpec, container);

        assertThat(podSpec.getVolumes()).hasSize(2);
        assertThat(podSpec.getVolumes())
                .extracting(V1Volume::getName)
                .containsExactly("existing-volume", "devshm");

        V1Volume devshmVolume = podSpec.getVolumes().get(1);
        assertThat(devshmVolume.getEmptyDir()).isNotNull();
        assertThat(devshmVolume.getEmptyDir().getMedium()).isEqualTo("Memory");
        assertThat(devshmVolume.getEmptyDir().getSizeLimit().toSuffixedString()).isEqualTo("64Mi");

        assertThat(container.getVolumeMounts()).hasSize(2);

        V1VolumeMount firstMount = container.getVolumeMounts().get(0);
        assertThat(firstMount.getName()).isEqualTo("existing-mount");
        assertThat(firstMount.getMountPath()).isEqualTo("/existing");

        V1VolumeMount secondMount = container.getVolumeMounts().get(1);
        assertThat(secondMount.getName()).isEqualTo("devshm");
        assertThat(secondMount.getMountPath()).isEqualTo("/dev/shm");
    }

    @Test
    public void testAddSharedMemory_withNullSize_doesNothing() {
        SharedMemory sharedMemory = SharedMemory.newBuilder().build();

        Spec spec = new Spec();
        spec.setVolumes(new ArrayList<>());

        Container container = new Container();
        container.setVolumeMounts(new ArrayList<>());

        WorkflowComponentUtils.addSharedMemory(sharedMemory, spec, container);

        assertThat(spec.getVolumes()).isEmpty();
        assertThat(container.getVolumeMounts()).isEmpty();
    }

    @Test
    public void testAddSharedMemory_withEmptySize_doesNothing() {
        SharedMemory sharedMemory = SharedMemory.newBuilder().setSize("").build();

        Spec spec = new Spec();
        spec.setVolumes(new ArrayList<>());

        Container container = new Container();
        container.setVolumeMounts(new ArrayList<>());

        WorkflowComponentUtils.addSharedMemory(sharedMemory, spec, container);

        assertThat(spec.getVolumes()).isEmpty();
        assertThat(container.getVolumeMounts()).isEmpty();
    }

    @Test
    public void testAddSharedMemory_withValidSize_addsVolumeAndMount() {
        SharedMemory sharedMemory = SharedMemory.newBuilder().setSize("512Mi").build();

        Spec spec = new Spec();
        spec.setVolumes(new ArrayList<>());

        Container container = new Container();
        container.setVolumeMounts(new ArrayList<>());

        WorkflowComponentUtils.addSharedMemory(sharedMemory, spec, container);

        assertThat(spec.getVolumes()).hasSize(1);
        Volume volume = spec.getVolumes().get(0);
        assertThat(volume.getName()).isEqualTo("devshm");
        assertThat(volume.getEmptyDir()).isNotNull();
        assertThat(volume.getEmptyDir().getMedium()).isEqualTo("Memory");
        assertThat(volume.getEmptyDir().getSizeLimit()).isEqualTo("512Mi");

        assertThat(container.getVolumeMounts()).hasSize(1);
        VolumeMount mount = container.getVolumeMounts().get(0);
        assertThat(mount.getName()).isEqualTo("devshm");
        assertThat(mount.getMountPath()).isEqualTo("/dev/shm");
    }

    @Test
    public void testAddSharedMemory_whenAlreadyConfigured_addsNewVolumeAndMount() {

        Volume existingVolume = new Volume();
        existingVolume.setName("existingVolume");

        VolumeMount existingMount = new VolumeMount();
        existingMount.setName("existingMount");
        existingMount.setMountPath("/mnt/existing");

        SharedMemory sharedMemory = SharedMemory.newBuilder().setSize("256Mi").build();

        Spec spec = new Spec();
        spec.setVolumes(new ArrayList<>());
        spec.getVolumes().add(existingVolume);

        Container container = new Container();
        container.setVolumeMounts(new ArrayList<>());
        container.getVolumeMounts().add(existingMount);

        WorkflowComponentUtils.addSharedMemory(sharedMemory, spec, container);

        assertThat(spec.getVolumes()).hasSize(2);
        assertThat(spec.getVolumes())
                .extracting(Volume::getName)
                .containsExactlyInAnyOrder("existingVolume", "devshm");


        assertThat(container.getVolumeMounts()).hasSize(2);
        assertThat(container.getVolumeMounts())
                .extracting(VolumeMount::getName)
                .containsExactlyInAnyOrder("existingMount", "devshm");

        Volume newVolume = spec.getVolumes().stream()
                .filter(v -> "devshm".equals(v.getName()))
                .findFirst().orElse(null);

        assertThat(newVolume).isNotNull();
        assertThat(newVolume.getEmptyDir()).isNotNull();
        assertThat(newVolume.getEmptyDir().getMedium()).isEqualTo("Memory");
        assertThat(newVolume.getEmptyDir().getSizeLimit()).isEqualTo("256Mi");

        VolumeMount newMount = container.getVolumeMounts().stream()
                .filter(vm -> "devshm".equals(vm.getName()))
                .findFirst().orElse(null);

        assertThat(newMount).isNotNull();
        assertThat(newMount.getMountPath()).isEqualTo("/dev/shm");
    }

}