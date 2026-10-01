package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.net.URI;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class StacAssetTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STAC_ASSET = "{" +
            "  \"href\": \"http://path/to/asset\"," +
            "  \"roles\": [\"data\"]," +
            "  \"type\": \"tiff\"," +
            "  \"otherPropKey\": \"otherPropValue\"" +
            "}";

    @Test
    public void testDeserialize_DeserializesAllAttributesForStacAssetAndPutUnknownPropertiesInOtherField() throws Exception {
        Asset stacAsset = OBJECT_MAPPER.readValue(STAC_ASSET, Asset.class);

        Asset expectedStacAsset = new Asset();
        expectedStacAsset.setRoles(Collections.singletonList("data"));
        expectedStacAsset.setType("tiff");
        expectedStacAsset.setHref(URI.create("http://path/to/asset"));
        expectedStacAsset.getOther().put("otherPropKey", "otherPropValue");
        assertThat(stacAsset).isEqualTo(expectedStacAsset);
    }
}
