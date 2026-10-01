package com.cgi.eoss.platform.core.processing.outputuploader.stac;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StacDocumentTest {

    private static final String STAC_DOCUMENT = "{" +
            "  \"features\": []," +
            "  \"otherProp\": \"docProp\"" +
            "}";

    @Test
    public void testDeserialize_DeserializesAllAttributesForStacDocumentAndPutUnknownPropertiesInOtherPropField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StacDocument stacDocument = mapper.readValue(STAC_DOCUMENT, StacDocument.class);
        assertThat(stacDocument.getOtherProperties()).hasSize(1);
        assertThat(stacDocument.getOtherProperties()).containsEntry("otherProp", "docProp");
        assertThat(stacDocument.getFeatures()).hasSize(0);
    }

}