package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;


import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class StacItemTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STAC_ITEM = "{" +
            "  \"stac_version\": \"1.0.0\"," +
            "  \"id\": \"featureID\"," +
            "  \"type\": \"Feature\"," +
            "  \"geometry\": null," +
            "  \"properties\": { \"propKey\" : \"propValue\" }," +
            "  \"assets\": { \"assetName\" : { \"href\" : \"http://path/to/asset\"} }," +
            "  \"otherPropKey\": \"otherPropValue\"" +
            "}";

    @Test
    public void testDeserialize_DeserializesAllAttributesForStacItemAndPutUnknownPropertiesInOtherField() throws Exception {
        StacItem stacItem = OBJECT_MAPPER.readValue(STAC_ITEM, StacItem.class);

        StacItem expectedStacItem = new StacItem();
        expectedStacItem.setStacVersion("1.0.0");
        expectedStacItem.setId("featureID");
        expectedStacItem.setType("Feature");
        expectedStacItem.setProperties(ImmutableMap.of("propKey", "propValue"));
        expectedStacItem.setAssets(ImmutableMap.of("assetName", new Asset(URI.create("http://path/to/asset"), null, null)));
        expectedStacItem.getOther().put("otherPropKey", "otherPropValue");
        assertThat(stacItem).isEqualTo(expectedStacItem);
    }

}
