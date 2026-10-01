package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Represents an empty directory volume in a Kubernetes workflow configuration.
 * This volume type can be used to store temporary data during the execution
 * of a workflow step or task. The `medium` and `sizeLimit` properties
 * provide additional customization options for the volume.
 *
 * medium - Specifies the storage medium to use for this EmptyDir volume. If not set,
 *          the volume's data will be stored on the node's default medium (e.g., disk).
 *          Supported values include "Memory" for memory-backed storage.
 *
 * sizeLimit - Specifies the maximum size limit for this volume. The size limit
 *             restricts how much data can be stored in the EmptyDir volume. If not
 *             specified, the volume size is only limited by the capacity of the underlying
 *             node.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VolumeEmptyDir {

    private String medium;
    private String sizeLimit;
}
