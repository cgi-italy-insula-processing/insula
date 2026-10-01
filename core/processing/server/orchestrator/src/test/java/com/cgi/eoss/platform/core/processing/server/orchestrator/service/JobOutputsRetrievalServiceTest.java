package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacAssetRelocator;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacItemDeserializer;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobOutputsRetrievalServiceTest {

    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    private final Path outputFolder = fileSystem.getPath("/data/outputProducts/jobExtId/outputId");

    private static final Map<String, String> STAC_OUTPUT_METADATA = ImmutableMap.of("type", "STAC");

    private JobDataService jobDataService;
    private JobOutputLocator jobOutputLocator;
    private StacItemDeserializer stacItemDeserializer;
    private StacAssetRelocator stacAssetRelocator;

    private InOrder inOrder;

    private JobOutputsRetrievalService jobOutputsRetrievalService;

    @Before
    public void init() throws Exception {
        jobDataService = mock(JobDataService.class);
        jobOutputLocator = mock(JobOutputLocator.class);
        stacItemDeserializer = mock(StacItemDeserializer.class);
        stacAssetRelocator = mock(StacAssetRelocator.class);

        inOrder = inOrder(jobDataService, jobOutputLocator, stacItemDeserializer, stacAssetRelocator);

        jobOutputsRetrievalService = new JobOutputsRetrievalService(jobDataService, jobOutputLocator,
                stacItemDeserializer, stacAssetRelocator, new URL("http://insula-test/api/v2"));
    }

    @After
    public void shutDown() throws Exception {
        fileSystem.close();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRetrieveOutputFile_ThrowsPlatformEntityNotFoundException_WhenJobDoesNotExist() {

        Long jobId = 71L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobOutputsRetrievalService.retrieveOutputFile(jobId, "outputId", "fileName"))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Job with id 71 not found");

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testRetrieveOutputFile_ReturnsPathOfTheOutputFileOfTheJob() {

        Long jobId = 83L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(createJob(jobId, "jobExtId")));
        when(jobOutputLocator.getOutputFile("jobExtId", "outputId", "fileName"))
                .thenReturn(outputFolder.resolve("fileName"));

        assertThat(jobOutputsRetrievalService.retrieveOutputFile(jobId, "outputId", "fileName"))
                .isEqualTo(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/fileName"));

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFile("jobExtId", "outputId", "fileName");
    }

    @Test
    public void testRetrieveAsStacCollection_ThrowsPlatformEntityNotFoundException_WhenJobDoesNotExist() {

        Long jobId = 99L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId"))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Job with id 99 not found");

        inOrder.verify(jobDataService).getById(jobId);
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsEmptyCollection_WhenJobHasNoOutputFiles() {

        Long jobId = 113L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(createJob(jobId, "jobExtId")));
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(Collections.emptyList());
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
        inOrder.verify(stacAssetRelocator).relocateAssets(Collections.emptyList(),
                "http://insula-test/api/v2/jobs/113/outputs/outputId?filename={filename}");

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", Collections.emptyList()));
    }

    @Test
    public void testRetrieveAsStacCollection_ThrowsIllegalStateException_WhenBaseUrlIsNotValid() throws Exception {

        jobOutputsRetrievalService = new JobOutputsRetrievalService(jobDataService, jobOutputLocator,
                stacItemDeserializer, stacAssetRelocator, new URL("http://insula test"));

        Long jobId = 131L;
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(createJob(jobId, "jobExtId")));
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(Collections.emptyList());
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);

        assertThatThrownBy(() -> jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid job output URL: http://insula test/jobs/131/outputs/outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithOneDefaultItemPerOutputFileNestedFoldersIncluded_WhenOutputIsNotStac() throws Exception {

        Long jobId = 173L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Instant nestedDataFileLastModified = Instant.parse("2026-09-24T10:15:30Z");
        Path nestedDataFile = createOutputFile(outputFolder, "nested/data.tif", nestedDataFileLastModified);
        Instant dataFileLastModified = Instant.parse("2026-09-25T11:16:31Z");
        Path dataFile = createOutputFile(outputFolder, "data.tif", dataFileLastModified);
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(nestedDataFile, dataFile));
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);

        StacItem nestedDataItem = createItem("jobExtId_outputId_nested/data.tif", "enclosure", URI.create("nested/data.tif"));
        when(stacItemDeserializer.defaultItem("jobExtId_outputId_nested/data.tif", nestedDataFileLastModified, "nested/data.tif"))
                .thenReturn(nestedDataItem);
        StacItem dataItem = createItem("jobExtId_outputId_data.tif", "enclosure", URI.create("data.tif"));
        when(stacItemDeserializer.defaultItem("jobExtId_outputId_data.tif", dataFileLastModified, "data.tif"))
                .thenReturn(dataItem);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("jobExtId_outputId_nested/data.tif",
                Instant.parse("2026-09-24T10:15:30Z"), "nested/data.tif");
        inOrder.verify(stacItemDeserializer).defaultItem("jobExtId_outputId_data.tif",
                Instant.parse("2026-09-25T11:16:31Z"), "data.tif");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(nestedDataItem, dataItem),
                "http://insula-test/api/v2/jobs/173/outputs/outputId?filename={filename}");

        Asset expectedNestedDataAsset = new Asset();
        expectedNestedDataAsset.setHref(URI.create("nested/data.tif"));
        StacItem expectedNestedDataItem = new StacItem();
        expectedNestedDataItem.setId("jobExtId_outputId_nested/data.tif");
        expectedNestedDataItem.setAssets(ImmutableMap.of("enclosure", expectedNestedDataAsset));

        Asset expectedDataAsset = new Asset();
        expectedDataAsset.setHref(URI.create("data.tif"));
        StacItem expectedDataItem = new StacItem();
        expectedDataItem.setId("jobExtId_outputId_data.tif");
        expectedDataItem.setAssets(ImmutableMap.of("enclosure", expectedDataAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection",
                ImmutableList.of(expectedNestedDataItem, expectedDataItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ThrowsUncheckedIOException_WhenOutputIsNotStacAndAnOutputFileDoesNotExist() {

        Long jobId = 181L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", ImmutableMap.of("type", "GeoTIFF")));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Path missingFile = outputFolder.resolve("missing.tif");
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(missingFile));
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);

        assertThatThrownBy(() -> jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Cannot read the modification time of job output file "
                        + "/data/outputProducts/jobExtId/outputId/missing.tif");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithDefaultItems_WhenJobServiceDescriptorHasNoOutputs() throws Exception {

        Long jobId = 199L;
        Job job = createJob(jobId, "jobExtId");
        job.getConfig().getService().getServiceDescriptor().setDataOutputs(null);
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Instant itemFileLastModified = Instant.parse("2026-09-24T10:15:30Z");
        Path itemFile = createOutputFile(outputFolder, "item.json", itemFileLastModified);
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(itemFile));
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);
        StacItem item = createItem("jobExtId_outputId_item.json", "enclosure", URI.create("item.json"));
        when(stacItemDeserializer.defaultItem("jobExtId_outputId_item.json", itemFileLastModified, "item.json")).thenReturn(item);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("jobExtId_outputId_item.json",
                Instant.parse("2026-09-24T10:15:30Z"), "item.json");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/199/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("item.json"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("jobExtId_outputId_item.json");
        expectedItem.setAssets(ImmutableMap.of("enclosure", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithDefaultItems_WhenOnlyAnotherOutputOfTheJobIsStac() throws Exception {

        Long jobId = 209L;
        Job job = createJob(jobId, "jobExtId", createOutput("anotherOutputId", STAC_OUTPUT_METADATA));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Instant itemFileLastModified = Instant.parse("2026-09-24T10:15:30Z");
        Path itemFile = createOutputFile(outputFolder, "item.json", itemFileLastModified);
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(itemFile));
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);
        StacItem item = createItem("jobExtId_outputId_item.json", "enclosure", URI.create("item.json"));
        when(stacItemDeserializer.defaultItem("jobExtId_outputId_item.json", itemFileLastModified, "item.json")).thenReturn(item);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("jobExtId_outputId_item.json",
                Instant.parse("2026-09-24T10:15:30Z"), "item.json");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/209/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("item.json"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("jobExtId_outputId_item.json");
        expectedItem.setAssets(ImmutableMap.of("enclosure", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithDefaultItems_WhenOutputHasNoPlatformMetadata() throws Exception {

        Long jobId = 218L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", null));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Instant itemFileLastModified = Instant.parse("2026-09-24T10:15:30Z");
        Path itemFile = createOutputFile(outputFolder, "item.json", itemFileLastModified);
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(itemFile));
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);
        StacItem item = createItem("jobExtId_outputId_item.json", "enclosure", URI.create("item.json"));
        when(stacItemDeserializer.defaultItem("jobExtId_outputId_item.json", itemFileLastModified, "item.json")).thenReturn(item);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("jobExtId_outputId_item.json",
                Instant.parse("2026-09-24T10:15:30Z"), "item.json");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/218/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("item.json"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("jobExtId_outputId_item.json");
        expectedItem.setAssets(ImmutableMap.of("enclosure", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithDefaultItems_WhenOutputPlatformMetadataHasNoType() throws Exception {

        Long jobId = 227L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", ImmutableMap.of("format", "STAC")));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Instant itemFileLastModified = Instant.parse("2026-09-24T10:15:30Z");
        Path itemFile = createOutputFile(outputFolder, "item.json", itemFileLastModified);
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(itemFile));
        when(jobOutputLocator.getOutputFolder("jobExtId", "outputId")).thenReturn(outputFolder);
        StacItem item = createItem("jobExtId_outputId_item.json", "enclosure", URI.create("item.json"));
        when(stacItemDeserializer.defaultItem("jobExtId_outputId_item.json", itemFileLastModified, "item.json")).thenReturn(item);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("jobExtId_outputId_item.json",
                Instant.parse("2026-09-24T10:15:30Z"), "item.json");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/227/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("item.json"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("jobExtId_outputId_item.json");
        expectedItem.setAssets(ImmutableMap.of("enclosure", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithTheItemsReadFromTheJsonOutputFiles_WhenOutputIsStac() {

        Long jobId = 236L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", STAC_OUTPUT_METADATA));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Path dataFile = outputFolder.resolve("data.tif");
        Path itemAFile = outputFolder.resolve("itemA.json");
        Path itemBFile = outputFolder.resolve("itemB.json");
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId"))
                .thenReturn(ImmutableList.of(dataFile, itemAFile, itemBFile));

        StacItem itemA = createItem("itemA", "data", URI.create("data.tif"));
        when(stacItemDeserializer.readItem(itemAFile)).thenReturn(Optional.of(itemA));
        StacItem itemB = createItem("itemB", "overview", URI.create("overview.png"));
        when(stacItemDeserializer.readItem(itemBFile)).thenReturn(Optional.of(itemB));

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).readItem(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/itemA.json"));
        inOrder.verify(stacItemDeserializer).readItem(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/itemB.json"));
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(itemA, itemB),
                "http://insula-test/api/v2/jobs/236/outputs/outputId?filename={filename}");

        Asset expectedItemAAsset = new Asset();
        expectedItemAAsset.setHref(URI.create("data.tif"));
        StacItem expectedItemA = new StacItem();
        expectedItemA.setId("itemA");
        expectedItemA.setAssets(ImmutableMap.of("data", expectedItemAAsset));

        Asset expectedItemBAsset = new Asset();
        expectedItemBAsset.setHref(URI.create("overview.png"));
        StacItem expectedItemB = new StacItem();
        expectedItemB.setId("itemB");
        expectedItemB.setAssets(ImmutableMap.of("overview", expectedItemBAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection",
                ImmutableList.of(expectedItemA, expectedItemB)));
    }

    @Test
    public void testRetrieveAsStacCollection_SkipsTheJsonOutputFilesThatAreNotStacItems_WhenOutputIsStac() {

        Long jobId = 266L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", STAC_OUTPUT_METADATA));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Path itemFile = outputFolder.resolve("item.json");
        Path notAnItemFile = outputFolder.resolve("notAnItem.json");
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(itemFile, notAnItemFile));

        StacItem item = createItem("item", "data", URI.create("data.tif"));
        when(stacItemDeserializer.readItem(itemFile)).thenReturn(Optional.of(item));
        when(stacItemDeserializer.readItem(notAnItemFile)).thenReturn(Optional.empty());

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).readItem(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/item.json"));
        inOrder.verify(stacItemDeserializer).readItem(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/notAnItem.json"));
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/266/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("data.tif"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");
        expectedItem.setAssets(ImmutableMap.of("data", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithTheItemsReadFromTheJsonOutputFiles_WhenOutputTypeIsStacInLowerCase() {

        Long jobId = 292L;
        Job job = createJob(jobId, "jobExtId", createOutput("outputId", ImmutableMap.of("type", "stac")));
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(job));

        Path itemFile = outputFolder.resolve("item.json");
        when(jobOutputLocator.getOutputFiles("jobExtId", "outputId")).thenReturn(ImmutableList.of(itemFile));
        StacItem item = createItem("item", "data", URI.create("data.tif"));
        when(stacItemDeserializer.readItem(itemFile)).thenReturn(Optional.of(item));

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobOutputLocator).getOutputFiles("jobExtId", "outputId");
        inOrder.verify(stacItemDeserializer).readItem(fileSystem.getPath("/data/outputProducts/jobExtId/outputId/item.json"));
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(item),
                "http://insula-test/api/v2/jobs/292/outputs/outputId?filename={filename}");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create("data.tif"));
        StacItem expectedItem = new StacItem();
        expectedItem.setId("item");
        expectedItem.setAssets(ImmutableMap.of("data", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsEmptyCollection_WhenJobIsParentAndHasNoSubJobs() {

        Long jobId = 430L;
        Job parentJob = createJob(jobId, "parentJobExtId");
        parentJob.setParent(true);
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(parentJob));
        when(jobDataService.getSubJobIds(parentJob)).thenReturn(Collections.emptyList());

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(parentJob);

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", Collections.emptyList()));
    }

    @Test
    public void testRetrieveAsStacCollection_AggregatesTheItemsOfTheSubJobs_WhenJobIsParent() throws Exception {

        Long jobId = 448L;
        Job parentJob = createJob(jobId, "parentJobExtId");
        parentJob.setParent(true);
        when(jobDataService.getById(jobId)).thenReturn(Optional.of(parentJob));

        Long subJobAId = 453L;
        Long subJobBId = 454L;
        when(jobDataService.getSubJobIds(parentJob)).thenReturn(ImmutableList.of(subJobAId, subJobBId));
        when(jobDataService.findByIds(ImmutableList.of(subJobAId, subJobBId))).thenReturn(ImmutableList.of(
                createJob(subJobAId, "subJobAExtId"),
                createJob(subJobBId, "subJobBExtId")));

        Path subJobAOutputFolder = fileSystem.getPath("/data/outputProducts/subJobAExtId/outputId");
        Instant subJobAFileLastModified = Instant.parse("2026-09-24T10:15:30Z");
        Path subJobAFile = createOutputFile(subJobAOutputFolder, "a.tif", subJobAFileLastModified);
        when(jobOutputLocator.getOutputFiles("subJobAExtId", "outputId")).thenReturn(ImmutableList.of(subJobAFile));
        when(jobOutputLocator.getOutputFolder("subJobAExtId", "outputId")).thenReturn(subJobAOutputFolder);
        StacItem subJobAItem = createItem("subJobAExtId_outputId_a.tif", "enclosure", URI.create("a.tif"));
        when(stacItemDeserializer.defaultItem("subJobAExtId_outputId_a.tif", subJobAFileLastModified, "a.tif"))
                .thenReturn(subJobAItem);

        Path subJobBOutputFolder = fileSystem.getPath("/data/outputProducts/subJobBExtId/outputId");
        Instant subJobBFileLastModified = Instant.parse("2026-09-25T11:16:31Z");
        Path subJobBFile = createOutputFile(subJobBOutputFolder, "b.tif", subJobBFileLastModified);
        when(jobOutputLocator.getOutputFiles("subJobBExtId", "outputId")).thenReturn(ImmutableList.of(subJobBFile));
        when(jobOutputLocator.getOutputFolder("subJobBExtId", "outputId")).thenReturn(subJobBOutputFolder);
        StacItem subJobBItem = createItem("subJobBExtId_outputId_b.tif", "enclosure", URI.create("b.tif"));
        when(stacItemDeserializer.defaultItem("subJobBExtId_outputId_b.tif", subJobBFileLastModified, "b.tif"))
                .thenReturn(subJobBItem);

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(jobId, "outputId");

        inOrder.verify(jobDataService).getById(jobId);
        inOrder.verify(jobDataService).getSubJobIds(parentJob);
        inOrder.verify(jobDataService).findByIds(ImmutableList.of(453L, 454L));
        inOrder.verify(jobOutputLocator).getOutputFiles("subJobAExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("subJobAExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("subJobAExtId_outputId_a.tif",
                Instant.parse("2026-09-24T10:15:30Z"), "a.tif");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(subJobAItem),
                "http://insula-test/api/v2/jobs/453/outputs/outputId?filename={filename}");
        inOrder.verify(jobOutputLocator).getOutputFiles("subJobBExtId", "outputId");
        inOrder.verify(jobOutputLocator).getOutputFolder("subJobBExtId", "outputId");
        inOrder.verify(stacItemDeserializer).defaultItem("subJobBExtId_outputId_b.tif",
                Instant.parse("2026-09-25T11:16:31Z"), "b.tif");
        inOrder.verify(stacAssetRelocator).relocateAssets(ImmutableList.of(subJobBItem),
                "http://insula-test/api/v2/jobs/454/outputs/outputId?filename={filename}");

        Asset expectedSubJobAAsset = new Asset();
        expectedSubJobAAsset.setHref(URI.create("a.tif"));
        StacItem expectedSubJobAItem = new StacItem();
        expectedSubJobAItem.setId("subJobAExtId_outputId_a.tif");
        expectedSubJobAItem.setAssets(ImmutableMap.of("enclosure", expectedSubJobAAsset));

        Asset expectedSubJobBAsset = new Asset();
        expectedSubJobBAsset.setHref(URI.create("b.tif"));
        StacItem expectedSubJobBItem = new StacItem();
        expectedSubJobBItem.setId("subJobBExtId_outputId_b.tif");
        expectedSubJobBItem.setAssets(ImmutableMap.of("enclosure", expectedSubJobBAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection",
                ImmutableList.of(expectedSubJobAItem, expectedSubJobBItem)));
    }

    private static Job createJob(Long jobId, String jobExtId, PlatformServiceDescriptor.Parameter... outputs) {
        PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
        serviceDescriptor.setDataOutputs(ImmutableList.copyOf(outputs));

        JobConfig jobConfig = new JobConfig();
        jobConfig.setService(ProcessingCoreEntities.createPlatformService(ProcessingCoreEntities.createUser().build())
                .platformServiceDescriptor(serviceDescriptor)
                .build());

        Job job = new Job();
        job.setId(jobId);
        job.setExtId(jobExtId);
        job.setConfig(jobConfig);
        return job;
    }

    private static PlatformServiceDescriptor.Parameter createOutput(String outputId, Map<String, String> platformMetadata) {
        return PlatformServiceDescriptor.Parameter.builder()
                .id(outputId)
                .platformMetadata(platformMetadata)
                .build();
    }

    private static StacItem createItem(String itemId, String assetKey, URI assetHref) {
        Asset asset = new Asset();
        asset.setHref(assetHref);

        StacItem item = new StacItem();
        item.setId(itemId);
        item.setAssets(ImmutableMap.of(assetKey, asset));
        return item;
    }

    private static Path createOutputFile(Path folder, String filePath, Instant lastModified) throws Exception {
        Path outputFile = folder.resolve(filePath);
        Files.createDirectories(outputFile.getParent());
        Files.createFile(outputFile);
        Files.setLastModifiedTime(outputFile, FileTime.from(lastModified));
        return outputFile;
    }

}
