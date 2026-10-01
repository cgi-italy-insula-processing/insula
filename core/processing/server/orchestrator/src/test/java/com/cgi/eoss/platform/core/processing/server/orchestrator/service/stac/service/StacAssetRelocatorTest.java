package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StacAssetRelocatorTest {

    private final StacAssetRelocator stacAssetRelocator = new StacAssetRelocator();

    @Test
    public void testRelocateAssets_ThrowsIllegalArgumentException_WhenTemplateDoesNotHoldTheFileNamePlaceholder() {

        StacItem item = createItem("item", "data", URI.create("data.tif"));

        assertThatThrownBy(() -> stacAssetRelocator.relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/131/outputs/outputId?filename="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File download URL template http://insula-test/api/v2/jobs/131/outputs/outputId?filename="
                        + " does not hold the file name placeholder {filename}");

        Asset expectedUntouchedAsset = new Asset();
        expectedUntouchedAsset.setHref(URI.create("data.tif"));
        StacItem expectedUntouchedItem = new StacItem();
        expectedUntouchedItem.setId("item");
        expectedUntouchedItem.setAssets(ImmutableMap.of("data", expectedUntouchedAsset));

        assertThat(item).isEqualTo(expectedUntouchedItem);
    }

    @Test
    public void testRelocateAssets_RelocatesTheHrefOfEveryAssetOfEveryItem() {

        StacItem itemA = createItem("itemA", "data", URI.create("data.tif"));

        Asset overviewAsset = new Asset();
        overviewAsset.setHref(URI.create("overview.png"));
        Asset thumbnailAsset = new Asset();
        thumbnailAsset.setHref(URI.create("thumbnails/thumbnail.png"));
        StacItem itemB = new StacItem();
        itemB.setId("itemB");
        itemB.setAssets(ImmutableMap.of("overview", overviewAsset, "thumbnail", thumbnailAsset));

        stacAssetRelocator.relocateAssets(ImmutableList.of(itemA, itemB),
                "http://insula-test/api/v2/jobs/149/outputs/outputId?filename={filename}");

        Asset expectedItemAAsset = new Asset();
        expectedItemAAsset.setHref(URI.create("http://insula-test/api/v2/jobs/149/outputs/outputId?filename=data.tif"));
        StacItem expectedItemA = new StacItem();
        expectedItemA.setId("itemA");
        expectedItemA.setAssets(ImmutableMap.of("data", expectedItemAAsset));

        Asset expectedOverviewAsset = new Asset();
        expectedOverviewAsset.setHref(URI.create("http://insula-test/api/v2/jobs/149/outputs/outputId?filename=overview.png"));
        Asset expectedThumbnailAsset = new Asset();
        expectedThumbnailAsset.setHref(URI.create(
                "http://insula-test/api/v2/jobs/149/outputs/outputId?filename=thumbnails%2Fthumbnail.png"));
        StacItem expectedItemB = new StacItem();
        expectedItemB.setId("itemB");
        expectedItemB.setAssets(ImmutableMap.of("overview", expectedOverviewAsset, "thumbnail", expectedThumbnailAsset));

        assertThat(itemA).isEqualTo(expectedItemA);
        assertThat(itemB).isEqualTo(expectedItemB);
    }

    @Test
    public void testRelocateAssets_RelocatesTheHrefWithTheFilePathInPlaceOfThePlaceholder_WhenPlaceholderIsInThePath() {

        StacItem item = createItem("item", "data", URI.create("dir/relative%20encoded.tif"));

        stacAssetRelocator.relocateAssets(ImmutableList.of(item), "http://insula-test/files/{filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("http://insula-test/files/dir%2Frelative%20encoded.tif"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");
        expectedItem.setAssets(ImmutableMap.of("data", expectedAsset));

        assertThat(item).isEqualTo(expectedItem);
    }

    @Test
    public void testRelocateAssets_ResolvesTheDotSegmentsOfTheRelativePath() {

        Asset currentFolderAsset = new Asset();
        currentFolderAsset.setHref(URI.create("./data.tif"));
        Asset parentFolderAsset = new Asset();
        parentFolderAsset.setHref(URI.create("thumbnails/../overview.png"));
        StacItem item = new StacItem();
        item.setId("item");
        item.setAssets(ImmutableMap.of("data", currentFolderAsset, "overview", parentFolderAsset));

        stacAssetRelocator.relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/225/outputs/outputId?filename={filename}");

        Asset expectedCurrentFolderAsset = new Asset();
        expectedCurrentFolderAsset.setHref(URI.create("http://insula-test/api/v2/jobs/225/outputs/outputId?filename=data.tif"));
        Asset expectedParentFolderAsset = new Asset();
        expectedParentFolderAsset.setHref(URI.create("http://insula-test/api/v2/jobs/225/outputs/outputId?filename=overview.png"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");
        expectedItem.setAssets(ImmutableMap.of("data", expectedCurrentFolderAsset, "overview", expectedParentFolderAsset));

        assertThat(item).isEqualTo(expectedItem);
    }

    @Test
    public void testRelocateAssets_LeavesUntouchedTheItemsWithoutAssets() {

        StacItem itemWithoutAssets = new StacItem();
        itemWithoutAssets.setId("item");

        stacAssetRelocator.relocateAssets(ImmutableList.of(itemWithoutAssets),
                "http://insula-test/api/v2/jobs/316/outputs/outputId?filename={filename}");

        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");

        assertThat(itemWithoutAssets).isEqualTo(expectedItem);
    }

    @Test
    public void testRelocateAssets_LeavesUntouchedTheAssetsWithoutHref() {

        StacItem item = createItem("item", "data", null);

        stacAssetRelocator.relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/340/outputs/outputId?filename={filename}");

        Asset expectedAssetWithoutHref = new Asset();
        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");
        expectedItem.setAssets(ImmutableMap.of("data", expectedAssetWithoutHref));

        assertThat(item).isEqualTo(expectedItem);
    }

    @Test
    public void testRelocateAssets_PercentEncodesTheFilePath_WhenFilePathContainsCharactersNotAllowedInUrlQueries() {

        StacItem item = createItem("item", "data", URI.create("nested/a%20b%26c%3Dd%2Be.tif"));

        stacAssetRelocator.relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/362/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create(
                "http://insula-test/api/v2/jobs/362/outputs/outputId?filename=nested%2Fa%20b%26c%3Dd%2Be.tif"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");
        expectedItem.setAssets(ImmutableMap.of("data", expectedAsset));

        assertThat(item).isEqualTo(expectedItem);
    }

    @Test
    public void testRelocateAssets_RelocatesOnlyTheAssetsReferencingRelativeFilePaths() throws Exception {

        StacItem item = new ObjectMapper().readValue(
                Paths.get("src", "test", "resources", "stac", "stac-item-assets-with-multiple-href-types.json").toFile(),
                StacItem.class);

        stacAssetRelocator.relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/386/outputs/outputId?filename={filename}");

        Map<String, Asset> assets = item.getAssets();
        assertThat(assets).hasSize(11);

        // relative file paths are relocated to the download URL of the referenced file, folders included
        assertThat(assets.get("relative-plain").getHref())
                .isEqualTo(URI.create("http://insula-test/api/v2/jobs/386/outputs/outputId?filename=relativePlain.tif"));
        assertThat(assets.get("relative-parent").getHref())
                .isEqualTo(URI.create("http://insula-test/api/v2/jobs/386/outputs/outputId?filename=..%2FrelativeParent.tif"));
        assertThat(assets.get("relative-encoded").getHref())
                .isEqualTo(URI.create("http://insula-test/api/v2/jobs/386/outputs/outputId?filename=dir%2Frelative%20encoded.tif"));

        // any other href is left untouched
        assertThat(assets.get("relative-dir").getHref()).isEqualTo(URI.create("dir/"));
        assertThat(assets.get("relative-dot").getHref()).isEqualTo(URI.create("."));
        assertThat(assets.get("relative-dotdot").getHref()).isEqualTo(URI.create(".."));
        assertThat(assets.get("absolute-path").getHref()).isEqualTo(URI.create("/absolutePath.tif"));
        assertThat(assets.get("absolute-scheme").getHref()).isEqualTo(URI.create("http://host/absoluteScheme.tif"));
        assertThat(assets.get("network-path").getHref()).isEqualTo(URI.create("//host/networkPath.tif"));
        assertThat(assets.get("empty-path").getHref()).isEqualTo(URI.create("?v=2"));
        assertThat(assets.get("empty-href").getHref()).isEqualTo(URI.create(""));
    }

    private static StacItem createItem(String itemId, String assetKey, URI assetHref) {
        Asset asset = new Asset();
        asset.setHref(assetHref);

        StacItem item = new StacItem();
        item.setId(itemId);
        item.setAssets(ImmutableMap.of(assetKey, asset));
        return item;
    }

}
