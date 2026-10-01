package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.net.URI;
import java.net.URL;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JobInputTest {

    private static final String FORMAT = "format";

    private static final String CATALOGUE = "CATALOGUE";

    private static final String OTHER = "OTHER";

    private static final String PREVENT_URL_DOWNLOAD = "preventUrlDownload";

    private static final String DATA_TYPE = "dataType";

    private static final String STRING = "string";

    @Test
    public void testGetContentsAsStac_ReturnsListOfStacFeatures_WhenJobInputTypeIsSTAC() throws Exception{
        StacDocument.Asset asset = new StacDocument.Asset();
        asset.setHref(new URI("href"));
        asset.getOther().putAll(ImmutableMap.of("otherAssetPropertyKey", "otherAssetPropertyValue"));

        StacDocument.StacItem stacItem = new StacDocument.StacItem();
        stacItem.setStacVersion("1.2.0");
        stacItem.setId("featureId");
        stacItem.setAssets(ImmutableMap.of("assetKey", asset));
        stacItem.getOther().putAll(ImmutableMap.of("otherPropertyKey", "otherPropertyValue"));

        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .contents(ImmutableList.of(stacItem))
                .build();

        assertThat(jobInput.getContentsAsStac()).isEqualTo(ImmutableList.of(stacItem));
    }

    @Test
    public void testGetContentsAsStac_ThrowsIllegalStateException_WhenJobInputTypeIsNotSTAC() {
        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.URL)
                .contents(ImmutableList.of())
                .build();

        assertThatThrownBy(jobInput::getContentsAsStac)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Input is not of type STAC");
    }

    @Test
    public void testGetInternalReferences_ReturnsCollectionContainingJobInputInternalReference_WhenJobInputTypeIsSTAC() throws Exception {
        String internalReference = "http://some.url.example/path/to/catalog.json";
        String featureId = "featureId-123-letters-456";
        String value = "http://stac.document.url/path/to/search?query=example#".concat(featureId);

        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of(value))
                .internalReference(new URL(internalReference))
                .build();

        assertThat(jobInput.getInternalReferences()).isEqualTo(
                Collections.singletonList(internalReference)
        );
    }

    @Test
    public void testGetInternalReferences_ReturnsCollectionContainingJobInputInternalReferenceWithFragment_WhenJobInputTypeIsSTACAndIsParallelInput() throws Exception {
        String internalReference = "http://some.url.example/path/to/catalog.json";
        String featureId = "featureId-123-letters-456";
        String value = "http://stac.document.url/path/to/search?query=example#".concat(featureId);


        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of(value))
                .internalReference(new URL(internalReference))
                .parallelInput(true)
                .build();

        assertThat(jobInput.getInternalReferences()).isEqualTo(
                Collections.singletonList(internalReference.concat("#" + featureId))
        );
    }

    @Test
    public void testGetInternalReferences_ThrowsIllegalStateException_WhenJobInputTypeIsNotSTAC() {
        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.URL)
                .build();

        assertThatThrownBy(jobInput::getInternalReferences)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Input is not of type STAC");
    }

    @Test
    public void testGetInternalReferences_ThrowsIllegalStateException_WhenInternalReferenceUrlCannotBeParsed() throws Exception {
        String internalReference = "file://very&$$strange/&%Url";
        String featureId = "featureId-123-letters-456";
        String value = "http://stac.document.url/path/to/search?query=example#".concat(featureId);

        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of(value))
                .internalReference(new URL(internalReference))
                .build();

        assertThatThrownBy(jobInput::getInternalReferences)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.net.URISyntaxException: Malformed escape pair at index");
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeOTHER_WhenParameterIsNotDownloadableAndFormatIsOtherAndPreventUrlDownloadIsMissing() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, "number"))
                .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.OTHER);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeOTHER_WhenPlatformMetadataPreventsUrlDownload() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "true"))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.OTHER);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeURL_WhenPlatformMetadataIsMissing() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.URL);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeURL_WhenDataTypeIsStringAndPreventUrlDownloadIsFalse() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                .platformMetadata(ImmutableMap.of(PREVENT_URL_DOWNLOAD, "false"))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.URL);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeURL_WhenFormatIsCatalogueAndPreventUrlDownloadIsMissing() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.URL);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeURL_WhenFormatIsCatalogueAndPreventUrlDownloadIsFalse() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.URL);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeSTAC_WhePlatformMetadataInputTypeIsSTAC() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .platformMetadata(ImmutableMap.of("type", "STAC", FORMAT, CATALOGUE, PREVENT_URL_DOWNLOAD, "false"))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.STAC);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeURL_WhenDataTypeIsStringFormatIsOtherAndPreventUrlDownloadIsMissing() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.URL);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeOTHER_WhenDataTypeIsStringFormatIsAOIAndPreventUrlDownloadIsMissing() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                .platformMetadata(ImmutableMap.of(FORMAT, "AOI"))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.OTHER);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeOTHER_WhenDataTypeIsStringAndContainsAllowedValuesAndPreventUrlDownloadIsMissing() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING, "allowedValues", "value1,value2"))
                .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.OTHER);
    }

    @Test
    public void testGetTypeFromParameter_ReturnsTypeOTHER_WhenDataTypeIsStringFormatIsOtherAndPreventUrlDownloadIsTrue() {
        PlatformServiceDescriptor.Parameter parameter = PlatformServiceDescriptor.Parameter.builder()
                .defaultAttrs(ImmutableMap.of(DATA_TYPE, STRING))
                .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                .build();
        assertThat(JobInput.getTypeFromParameter(parameter)).isEqualTo(JobInput.Type.OTHER);
    }

    @Test
    public void testIsExploded_ReturnsTrue_WhenOneOfTheValuesContainsFragment() {
        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("http://url.com", "http://url.com#fragment"))
                .build();
        assertThat(jobInput.isExploded()).isTrue();
    }

    @Test
    public void testIsExploded_ReturnsFalse_WhenNoneOfTheValuesContainsFragment() {
        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("http://url.com", "http://url.com/path"))
                .build();
        assertThat(jobInput.isExploded()).isFalse();
    }

    @Test
    public void testGetValuesWithoutFragments_ReturnsValuesWithoutFragments() {
        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("http://url.com#fragment", "http://url.com/path#fragment"))
                .build();
        assertThat(jobInput.getValuesWithoutFragments()).containsExactlyInAnyOrder(
                "http://url.com", "http://url.com/path"
        );
    }

    @Test
    public void testGetValuesWithoutFragments_ReturnsOriginalValues_WhenNoneOfTheValuesContainsFragment() {
        JobInput jobInput = JobInput.builder()
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("http://url.com", "http://url.com/path"))
                .build();
        assertThat(jobInput.getValuesWithoutFragments()).containsExactlyInAnyOrder(
                "http://url.com", "http://url.com/path"
        );
    }

}
