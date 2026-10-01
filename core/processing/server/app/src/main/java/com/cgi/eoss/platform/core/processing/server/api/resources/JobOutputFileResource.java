package com.cgi.eoss.platform.core.processing.server.api.resources;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.hateoas.RepresentationModel;

/**
 * Representation of one file of a job output, linked to the endpoint it can be downloaded from.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JobOutputFileResource extends RepresentationModel<JobOutputFileResource> {

    private final String filename;

    /**
     * Creates the representation of a job output file.
     *
     * @param filename the name of the file, relative to the folder of the job output
     */
    public JobOutputFileResource(String filename) {
        this.filename = filename;
    }

}
