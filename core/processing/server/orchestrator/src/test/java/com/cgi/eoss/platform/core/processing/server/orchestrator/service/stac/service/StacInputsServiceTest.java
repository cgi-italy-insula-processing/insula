package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.amazonaws.services.s3.AmazonS3;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInput;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StacInputsServiceTest {

    private static final Path STAC_BASE_TEST_PATH = Paths.get("src", "test", "resources", "stac");

    private static final String BUCKET_NAME = "stac-items-bucket";

    private static final String USER_NAME = "userId";

    private static final String FEATURE_ID = "9106a7fe-9d4c-5673-9979-0de28035ea7f";

    private static final String STAC_URL = "https://example.org/api/stac-document.json";

    @Test
    public void testExplodeStacItems_DelegatesToFirstSupportingDownloaderAndDoesNotTryOthers() throws Exception {
        StacDownloader first = Mockito.mock(StacDownloader.class);
        StacDownloader second = Mockito.mock(StacDownloader.class);
        when(first.supports(any())).thenReturn(true);
        when(first.download(any(), eq(USER_NAME))).thenReturn(stacDocumentWithSingleFeature());

        StacInputsService service = createStacInputsService(ImmutableList.of(first, second), mock(AmazonS3.class));
        JobInput exploded = service.explodeStacItems(createStacJobInput(), "jobId", USER_NAME);

        assertThat(exploded.getContentsAsStac()).hasSize(1);
        assertThat(exploded.getContentsAsStac().get(0).getId()).isEqualTo(FEATURE_ID);
        verify(first).download(any(), eq(USER_NAME));
        verify(second, never()).download(any(), any());
    }

    @Test
    public void testExplodeStacItems_FallsBackToNextDownloader_WhenFirstFails() throws Exception {
        StacDownloader failing = Mockito.mock(StacDownloader.class);
        StacDownloader working = Mockito.mock(StacDownloader.class);
        when(failing.supports(any())).thenReturn(true);
        when(failing.download(any(), eq(USER_NAME))).thenThrow(new IllegalStateException("download failed"));
        when(working.supports(any())).thenReturn(true);
        when(working.download(any(), eq(USER_NAME))).thenReturn(stacDocumentWithSingleFeature());

        StacInputsService service = createStacInputsService(ImmutableList.of(failing, working), mock(AmazonS3.class));
        JobInput exploded = service.explodeStacItems(createStacJobInput(), "jobId", USER_NAME);

        assertThat(exploded.getContentsAsStac()).hasSize(1);
        assertThat(exploded.getContentsAsStac().get(0).getId()).isEqualTo(FEATURE_ID);
        verify(working).download(any(), eq(USER_NAME));
    }

    @Test
    public void testExplodeStacItems_SkipsDownloaderThatDoesNotSupportAndDelegatesToNext() throws Exception {
        StacDownloader notSupporting = Mockito.mock(StacDownloader.class);
        StacDownloader supporting = Mockito.mock(StacDownloader.class);
        when(notSupporting.supports(any())).thenReturn(false);
        when(supporting.supports(any())).thenReturn(true);
        when(supporting.download(any(), eq(USER_NAME))).thenReturn(stacDocumentWithSingleFeature());

        StacInputsService service = createStacInputsService(ImmutableList.of(notSupporting, supporting), mock(AmazonS3.class));
        JobInput exploded = service.explodeStacItems(createStacJobInput(), "jobId", USER_NAME);

        assertThat(exploded.getContentsAsStac()).hasSize(1);
        assertThat(exploded.getContentsAsStac().get(0).getId()).isEqualTo(FEATURE_ID);
        verify(notSupporting, never()).download(any(), any());
        verify(supporting).download(any(), eq(USER_NAME));
    }

    @Test
    public void testExplodeStacItems_ThrowsIllegalStateException_WhenNoDownloaderSupportsTheUrl() {
        StacDownloader downloader = Mockito.mock(StacDownloader.class);
        when(downloader.supports(any())).thenReturn(false);

        StacInputsService service = createStacInputsService(ImmutableList.of(downloader), mock(AmazonS3.class));

        assertThatThrownBy(() -> service.explodeStacItems(createStacJobInput(), "jobId", USER_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to download STAC Document");
        verify(downloader, never()).download(any(), any());
    }

    @Test
    public void testExplodeStacItems_ThrowsIllegalStateException_WhenAllSupportingDownloadersFail() {
        StacDownloader first = Mockito.mock(StacDownloader.class);
        StacDownloader second = Mockito.mock(StacDownloader.class);
        when(first.supports(any())).thenReturn(true);
        when(first.download(any(), eq(USER_NAME))).thenThrow(new IllegalStateException("first failed"));
        when(second.supports(any())).thenReturn(true);
        when(second.download(any(), eq(USER_NAME))).thenThrow(new IllegalStateException("second failed"));

        StacInputsService service = createStacInputsService(ImmutableList.of(first, second), mock(AmazonS3.class));

        assertThatThrownBy(() -> service.explodeStacItems(createStacJobInput(), "jobId", USER_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to download STAC Document");
    }

    @Test
    public void testExplodeStacItems_ThrowsIllegalArgumentException_WhenStacDocumentUrlIsNotValid() {
        StacDownloader downloader = Mockito.mock(StacDownloader.class);
        StacInputsService service = createStacInputsService(ImmutableList.of(downloader), mock(AmazonS3.class));
        JobInput jobInput = JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("veryInvalidUrl$%&$"))
                .build();

        assertThatThrownBy(() -> service.explodeStacItems(jobInput, "jobId", USER_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stac Document URL not valid");
    }

    @Test
    public void testExplodeStacItems_ReturnsJobInputAsItIs_WhenJobInputIsAlreadyExploded() throws Exception {
        StacDownloader downloader = Mockito.mock(StacDownloader.class);
        StacInputsService service = createStacInputsService(ImmutableList.of(downloader), mock(AmazonS3.class));
        StacDocument.StacItem stacItem = new StacDocument.StacItem();
        stacItem.setId(FEATURE_ID);
        stacItem.setStacVersion("1.0.0");
        JobInput jobInput = JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)

                .values(ImmutableList.of(STAC_URL + "#" + FEATURE_ID))
                .contents(ImmutableList.of(stacItem))
                .internalReference(new URL("https://example.org/bucket/catalog.json"))
                .build();

        assertThat(service.explodeStacItems(jobInput, "jobId", USER_NAME)).isEqualTo(jobInput);
        verify(downloader, never()).download(any(), any());
    }

    private StacInputsService createStacInputsService(List<StacDownloader> downloaders, AmazonS3 s3Client) {
        return new StacInputsService(new ObjectMapper(), s3Client,
                new DefaultStacItemsS3Bucket(BUCKET_NAME), downloaders);
    }

    private StacDocument stacDocumentWithSingleFeature() throws Exception {
        return new ObjectMapper().readValue(
                readAsString(STAC_BASE_TEST_PATH.resolve("stac-search-document.json")), StacDocument.class);
    }

    private JobInput createStacJobInput() {
        return JobInput.builder()
                .id("stacInput")
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of(STAC_URL))
                .build();
    }
}