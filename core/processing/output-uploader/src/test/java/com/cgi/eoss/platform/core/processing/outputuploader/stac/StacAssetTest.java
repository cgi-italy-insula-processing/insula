package com.cgi.eoss.platform.core.processing.outputuploader.stac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class StacAssetTest {

    private static final String STAC_ASSET =
            "        {\n" +
            "          \"href\": \"fakePath\",\n" +
            "          \"otherProp\": \"assetProp\"\n" +
            "        }\n";

    @Test
    public void testDeserialize_DeserializesAllAttributesForStacAssetAndPutUnknownPropertiesInOtherPropField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StacDocument.Asset stacAsset = mapper.readValue(STAC_ASSET, StacDocument.Asset.class);
        assertThat(stacAsset.getHref()).isEqualTo(Paths.get("fakePath"));
        assertThat(stacAsset.getOtherProperties()).hasSize(1);
        assertThat(stacAsset.getOtherProperties()).containsEntry("otherProp", "assetProp");
    }

}