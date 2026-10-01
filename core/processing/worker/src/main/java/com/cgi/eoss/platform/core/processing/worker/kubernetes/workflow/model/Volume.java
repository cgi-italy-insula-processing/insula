package com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 *
 * Class that represents mounted phisycal volumes.
 * Every volume has a specific name. If it should be persistent (not be recreated at restart)
 * it must referr to a specific persistentVolumeClaim.
 * A volume could also map a kubernetes configMap for loading specific workflow's properties.
 * In this case, configMap attribute should be not null and configured with the specific kubernetes config map name.
 *
 **/
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Volume {

	private String name;
	private VolumeClaimVolumeSource persistentVolumeClaim;
    private VolumeEmptyDir emptyDir;
	private ConfigMap configMap;
	
}
