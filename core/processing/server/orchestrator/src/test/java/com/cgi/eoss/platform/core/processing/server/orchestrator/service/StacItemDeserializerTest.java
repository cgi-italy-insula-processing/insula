package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacItemDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.log4j.Log4j2;
import org.junit.Test;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@Log4j2
public class StacItemDeserializerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Path TEST_ROOT = Paths.get("src", "test", "resources", "stac");

    private final StacItemDeserializer stacItemDeserializer = new StacItemDeserializer(OBJECT_MAPPER);

    @Test
    public void testDefaultItem_CreatesStacItemWithDefaultAttributesAndOneAssetReferencingTheFileThroughItsRelativePathAndTruncatesTheDatetimeToSeconds() {

        StacItem stacItem = stacItemDeserializer.defaultItem("stacItemId", Instant.parse("2020-01-15T10:00:00.987654321Z"),
                "nested/fileName");

        StacItem expectedStacItem = new StacItem();
        expectedStacItem.setStacVersion("1.1.0");
        expectedStacItem.setType("Feature");
        expectedStacItem.setId("stacItemId");
        expectedStacItem.setProperties(ImmutableMap.of("datetime", "2020-01-15T10:00:00Z"));
        Asset expectedStacAsset = new Asset();
        expectedStacAsset.setRoles(Collections.singletonList("data"));
        expectedStacAsset.setHref(URI.create("nested/fileName"));
        expectedStacItem.setAssets(ImmutableMap.of("enclosure", expectedStacAsset));

        assertThat(stacItem).isEqualTo(expectedStacItem);
    }

    @Test
    public void testDefaultItem_PercentEncodesTheAssetHref_WhenRelativePathContainsCharactersNotAllowedInUris() {

        StacItem stacItem = stacItemDeserializer.defaultItem("stacItemId", Instant.parse("2020-01-15T10:00:00Z"),
                "nested dir/a b:c#d%e.tif");

        assertThat(stacItem.getAssets().get("enclosure").getHref())
                .isEqualTo(URI.create("nested%20dir/a%20b:c%23d%25e.tif"));
    }

    @Test
    public void testDefaultItem_ThrowsIllegalArgumentException_WhenRelativePathStartsWithASegmentHoldingAColon() {

        try {
            stacItemDeserializer.defaultItem("stacItemId", Instant.parse("2020-01-15T10:00:00Z"), "a b:c.tif");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Cannot express the relative path a b:c.tif as a STAC asset href");
        }
    }

    @Test
    public void testReadItem_CreatesStacItemObjectFromProvidedJsonFile() {

        Path stacItemFile = TEST_ROOT.resolve("stac-item.json");

        StacItem stacItem = stacItemDeserializer.readItem(stacItemFile).get();

        StacItem expectedStacItem = new StacItem();
        expectedStacItem.setStacVersion("1.1.0");
        expectedStacItem.setType("Feature");
        expectedStacItem.setId("stacItemId");
        expectedStacItem.setProperties(ImmutableMap.of("datetime", "2020-01-15T10:00:00Z"));
        Asset expectedStacAsset = new Asset();
        expectedStacAsset.setRoles(Collections.singletonList("data"));
        expectedStacAsset.setHref(URI.create("./fileName"));
        expectedStacItem.setAssets(ImmutableMap.of("enclosure", expectedStacAsset));

        assertThat(stacItem).isEqualTo(expectedStacItem);
    }

    @Test
    public void testReadItem_ThrowsUncheckedIOException_WhenJsonFileDoesNotExist() {

        Path stacItemFile = Paths.get(UUID.randomUUID().toString());
        assertThat(stacItemFile).doesNotExist();

        try {
            stacItemDeserializer.readItem(stacItemFile);
            fail();
        } catch (UncheckedIOException e) {
            assertThat(e).hasMessage("Cannot read STAC item " + stacItemFile);
        }

    }

    @Test
    public void testReadItem_ReturnsEmptyOptional_WhenFileIsNotJson() throws Exception {

        Path stacItemFile = Files.createTempFile("stac-item", ".json");
        Files.write(stacItemFile, "not-valid-json".getBytes());

        assertThat(stacItemDeserializer.readItem(stacItemFile)).isEmpty();

    }
}
