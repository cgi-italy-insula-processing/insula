package com.cgi.eoss.platform.core.processing.server.model;

import com.cgi.eoss.platform.core.processing.server.model.converters.UriStringConverter;
import lombok.*;

import javax.persistence.*;
import java.net.URI;

@Data
@NoArgsConstructor
@Embeddable
public class Cwl {

    /**
     * <p>External reference for the CWL.</p>
     */
    @Convert(converter = UriStringConverter.class)
    private URI url;

    /**
     * <p>The content of the CWL, represented as free text.</p>
     */
    private String document;

    /**
     * Creates a new instance of the Cwl class with minimum required parameters.
     * @param url the CWL external reference url.
     * @param document the CWL content as free text.
     */
    public Cwl(URI url, String document) {
        this.url = url;
        this.document = document;
    }
}
