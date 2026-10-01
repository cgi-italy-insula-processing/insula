package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class StacDocumentTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STAC_DOCUMENT = "{" +
            "  \"type\": \"FeaturesCollection\"," +
            "  \"features\": [ {\"stac_version\" : \"1.0.0\", \"type\" : \"Feature\" }]," +
            "  \"otherPropKey\": \"otherPropValue\"" +
            "}";

    @Test
    public void testDeserialize_DeserializesAllAttributesForStacDocumentAndPutUnknownPropertiesInOtherField() throws Exception {
        StacDocument stacDocument = OBJECT_MAPPER.readValue(STAC_DOCUMENT, StacDocument.class);

        StacDocument expectedStacDocument = new StacDocument();
        expectedStacDocument.setFeatures(Collections.singletonList(new StacItem("1.0.0", null, "Feature", null, null, null)));
        expectedStacDocument.setType("FeaturesCollection");
        expectedStacDocument.getOther().put("otherPropKey", "otherPropValue");
        assertThat(stacDocument).isEqualTo(expectedStacDocument);
    }

}
