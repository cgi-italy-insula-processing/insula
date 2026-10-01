package com.cgi.eoss.platform.core.processing.outputuploader.stac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StacItemTest {

    private static final String STAC_ITEM =
            "    {\n" +
            "      \"stac_version\": \"1.0.0\",\n" +
            "      \"id\": \"itemId\",\n" +
            "      \"otherProp\": \"itemProp\",\n" +
            "      \"assets\": {\n" +
            "        \"asset\": {\n" +
            "        }\n" +
            "      }\n" +
            "    }\n";

    @Test
    public void testDeserialize_DeserializesAllAttributesForStacItemAndPutUnknownPropertiesInOtherPropField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StacDocument.StacItem stacItem = mapper.readValue(STAC_ITEM, StacDocument.StacItem.class);
        assertThat(stacItem.getStacVersion()).isEqualTo("1.0.0");
        assertThat(stacItem.getId()).isEqualTo("itemId");
        assertThat(stacItem.getAssets()).containsOnlyKeys("asset");
        assertThat(stacItem.getAssets().get("asset").getHref()).isNull();
        assertThat(stacItem.getAssets().get("asset").getOtherProperties()).isEmpty();
        assertThat(stacItem.getOtherProperties()).hasSize(1);
        assertThat(stacItem.getOtherProperties()).containsEntry("otherProp", "itemProp");
    }
}