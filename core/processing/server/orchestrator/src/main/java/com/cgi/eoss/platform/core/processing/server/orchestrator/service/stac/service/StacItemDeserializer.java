package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

/**
 * Class responsible to create STAC Items
 */
@Log4j2
@AllArgsConstructor
public class StacItemDeserializer {

    private final ObjectMapper objectMapper;

    /**
     * Creates a STAC Item with the minimum attributes set with default values and a single asset referencing a file
     * through its relative path
     *
     * @param itemId The identifier of the STAC Item
     * @param datetime The instant recorded in the datetime property of the STAC Item, truncated to seconds
     * @param assetRelativePath The path of the STAC Asset file relative to the folder holding the files of the job
     *                          output, referenced by the href of the asset
     *
     * @return A STAC Item object containing one asset
     */
    public StacItem defaultItem(String itemId, Instant datetime, String assetRelativePath) {
        StacItem item = new StacItem();
        item.setType("Feature");
        item.setStacVersion("1.1.0");
        item.setId(itemId);
        item.setGeometry(null);

        item.setProperties(ImmutableMap.of("datetime", toIsoString(datetime)));

        Asset asset = new Asset();
        asset.setHref(relativeHref(assetRelativePath));
        asset.setRoles(Collections.singletonList("data"));
        item.setAssets(ImmutableMap.of("enclosure", asset));
        return item;
    }

    /**
     * Deserializes a STAC Item object from a JSON file
     *
     * @param itemPath The path to the JSON file containing the STAC Item
     *
     * @return The deserialized STAC Item object
     */
    public Optional<StacItem> readItem(Path itemPath) {
        StacItem item;
        try {
            item = objectMapper.readValue(itemPath.toFile(), StacItem.class);
        } catch (JacksonException e) {
            LOG.warn("Skipping {}: {}", itemPath, e.getOriginalMessage());
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read STAC item " + itemPath, e);
        }
        return Optional.of(item);
    }

    private static URI relativeHref(String relativePath) {
        try {
            return new URI(null, null, relativePath, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot express the relative path " + relativePath
                    + " as a STAC asset href", e);
        }
    }

    private static String toIsoString(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

}
