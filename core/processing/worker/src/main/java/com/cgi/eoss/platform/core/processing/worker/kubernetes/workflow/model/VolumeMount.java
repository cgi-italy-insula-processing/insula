package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Class that represents specific physical volume mount point.
 * A VolumeMount instance must have a name and the name should refer to a related Volume name
 * In case the VolumeMounts refers to a folder, the mountPath attribute should contain the folder path.
 * In case of the mount of a single file, subPath field must contain specific file path too.
 * ***/
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VolumeMount {

	private String name;
	private String mountPath;
	private String subPath;
	private Boolean readOnly;

}
