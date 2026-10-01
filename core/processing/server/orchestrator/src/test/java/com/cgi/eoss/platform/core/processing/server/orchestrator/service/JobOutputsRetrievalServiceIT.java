package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.OrchestratorCoreTestConfig;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.server.persistence.exceptions.PlatformEntityNotFoundException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobConfigDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserDataService;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorCoreConfig.class, OrchestratorCoreTestConfig.class})
@TestPropertySource(locations = "classpath:test-orchestrator-core.properties")
public class JobOutputsRetrievalServiceIT {

    private static final Path STAC_ITEM_FILE = Paths.get("src", "test", "resources", "stac", "stac-item.json");

    @Autowired
    private JobOutputsRetrievalService jobOutputsRetrievalService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private JobConfigDataService jobConfigDataService;

    @Autowired
    private JobDataService jobDataService;

    @Value("${platform.orchestrator.outputProducts.baseDir}")
    private Path outputProductsBaseDir;

    private User owner;

    @Before
    public void init() {
        FilesUtils.deleteDirContentsIfExists(outputProductsBaseDir);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();

        owner = userDataService.save(ProcessingCoreEntities.createUser().build());
    }

    @After
    public void shutdown() {
        FilesUtils.deleteDirContentsIfExists(outputProductsBaseDir);

        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testRetrieveOutputFile_ThrowsPlatformEntityNotFoundException_WhenJobDoesNotExist() {

        assertThatThrownBy(() -> jobOutputsRetrievalService.retrieveOutputFile(424242L, "outputId", "result.tif"))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Job with id 424242 not found");
    }

    @Test
    public void testRetrieveOutputFile_ReturnsPathOfTheOutputFileOfTheJobNestedFoldersIncluded() throws Exception {

        Job job = persistJob("jobExtId", persistService(createOutput("outputId", ImmutableMap.of("type", "GeoTIFF"))));
        createOutputFile(job, "outputId", "nested/result.tif", "nested result content");

        Path retrievedFile = jobOutputsRetrievalService.retrieveOutputFile(job.getId(), "outputId", "nested/result.tif");

        assertThat(retrievedFile)
                .isEqualTo(Paths.get("target/data/outputProducts/jobExtId/outputId/nested/result.tif").toAbsolutePath());
        assertThat(retrievedFile).hasContent("nested result content");
    }

    @Test
    public void testRetrieveAsStacCollection_ThrowsPlatformEntityNotFoundException_WhenJobDoesNotExist() {

        assertThatThrownBy(() -> jobOutputsRetrievalService.retrieveAsStacCollection(424242L, "outputId"))
                .isInstanceOf(PlatformEntityNotFoundException.class)
                .hasMessage("Job with id 424242 not found");
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithTheItemsProducedByTheJobAndRelocatedAssets_WhenOutputIsStac() throws Exception {

        Job job = persistJob("jobExtId", persistService(createOutput("stacOutputId", ImmutableMap.of("type", "STAC"))));
        Files.copy(STAC_ITEM_FILE, createOutputFolder(job, "stacOutputId").resolve("stac-item.json"));
        createOutputFile(job, "stacOutputId", "fileName", "data content");

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(job.getId(), "stacOutputId");

        Asset expectedAsset = new Asset();
        expectedAsset.setHref(URI.create(
                "http://insula-test:8443/api/v2/jobs/" + job.getId() + "/outputs/stacOutputId?filename=fileName"));
        expectedAsset.setRoles(Collections.singletonList("data"));
        StacItem expectedItem = new StacItem();
        expectedItem.setStacVersion("1.1.0");
        expectedItem.setType("Feature");
        expectedItem.setId("stacItemId");
        expectedItem.setProperties(ImmutableMap.of("datetime", "2020-01-15T10:00:00Z"));
        expectedItem.setAssets(ImmutableMap.of("enclosure", expectedAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection", ImmutableList.of(expectedItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_ReturnsCollectionWithOneDefaultItemPerOutputFileNestedFoldersIncluded_WhenOutputIsNotStac() throws Exception {

        Job job = persistJob("jobExtId", persistService(createOutput("outputId", ImmutableMap.of("type", "GeoTIFF"))));
        createOutputFile(job, "outputId", "nested/result.tif", "result content", Instant.parse("2026-09-24T10:15:30Z"));
        createOutputFile(job, "outputId", "report.json", "{}", Instant.parse("2026-09-25T11:16:31Z"));

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(job.getId(), "outputId");

        Asset expectedResultAsset = new Asset();
        expectedResultAsset.setHref(URI.create(
                "http://insula-test:8443/api/v2/jobs/" + job.getId() + "/outputs/outputId?filename=nested%2Fresult.tif"));
        expectedResultAsset.setRoles(Collections.singletonList("data"));
        StacItem expectedResultItem = new StacItem();
        expectedResultItem.setStacVersion("1.1.0");
        expectedResultItem.setType("Feature");
        expectedResultItem.setId("jobExtId_outputId_nested/result.tif");
        expectedResultItem.setProperties(ImmutableMap.of("datetime", "2026-09-24T10:15:30Z"));
        expectedResultItem.setAssets(ImmutableMap.of("enclosure", expectedResultAsset));

        Asset expectedReportAsset = new Asset();
        expectedReportAsset.setHref(URI.create(
                "http://insula-test:8443/api/v2/jobs/" + job.getId() + "/outputs/outputId?filename=report.json"));
        expectedReportAsset.setRoles(Collections.singletonList("data"));
        StacItem expectedReportItem = new StacItem();
        expectedReportItem.setStacVersion("1.1.0");
        expectedReportItem.setType("Feature");
        expectedReportItem.setId("jobExtId_outputId_report.json");
        expectedReportItem.setProperties(ImmutableMap.of("datetime", "2026-09-25T11:16:31Z"));
        expectedReportItem.setAssets(ImmutableMap.of("enclosure", expectedReportAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection",
                ImmutableList.of(expectedResultItem, expectedReportItem)));
    }

    @Test
    public void testRetrieveAsStacCollection_AggregatesTheItemsOfTheSubJobs_WhenJobIsParent() throws Exception {

        Job parentJob = persistJob("parentJobExtId", persistService(createOutput("outputId", ImmutableMap.of("type", "GeoTIFF"))));
        parentJob.setParent(true);
        parentJob = jobDataService.save(parentJob);

        Job subJobA = persistSubJob("subJobAExtId", parentJob);
        createOutputFile(subJobA, "outputId", "a.tif", "a content", Instant.parse("2026-09-24T10:15:30Z"));
        Job subJobB = persistSubJob("subJobBExtId", parentJob);
        createOutputFile(subJobB, "outputId", "b.tif", "b content", Instant.parse("2026-09-25T11:16:31Z"));

        StacDocument stacCollection = jobOutputsRetrievalService.retrieveAsStacCollection(parentJob.getId(), "outputId");

        Asset expectedSubJobAAsset = new Asset();
        expectedSubJobAAsset.setHref(URI.create(
                "http://insula-test:8443/api/v2/jobs/" + subJobA.getId() + "/outputs/outputId?filename=a.tif"));
        expectedSubJobAAsset.setRoles(Collections.singletonList("data"));
        StacItem expectedSubJobAItem = new StacItem();
        expectedSubJobAItem.setStacVersion("1.1.0");
        expectedSubJobAItem.setType("Feature");
        expectedSubJobAItem.setId("subJobAExtId_outputId_a.tif");
        expectedSubJobAItem.setProperties(ImmutableMap.of("datetime", "2026-09-24T10:15:30Z"));
        expectedSubJobAItem.setAssets(ImmutableMap.of("enclosure", expectedSubJobAAsset));

        Asset expectedSubJobBAsset = new Asset();
        expectedSubJobBAsset.setHref(URI.create(
                "http://insula-test:8443/api/v2/jobs/" + subJobB.getId() + "/outputs/outputId?filename=b.tif"));
        expectedSubJobBAsset.setRoles(Collections.singletonList("data"));
        StacItem expectedSubJobBItem = new StacItem();
        expectedSubJobBItem.setStacVersion("1.1.0");
        expectedSubJobBItem.setType("Feature");
        expectedSubJobBItem.setId("subJobBExtId_outputId_b.tif");
        expectedSubJobBItem.setProperties(ImmutableMap.of("datetime", "2026-09-25T11:16:31Z"));
        expectedSubJobBItem.setAssets(ImmutableMap.of("enclosure", expectedSubJobBAsset));

        assertThat(stacCollection).isEqualTo(new StacDocument("FeatureCollection",
                ImmutableList.of(expectedSubJobAItem, expectedSubJobBItem)));
    }

    private PlatformService persistService(PlatformServiceDescriptor.Parameter output) {
        PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
        serviceDescriptor.setDataOutputs(ImmutableList.of(output));

        return serviceDataService.save(ProcessingCoreEntities.createPlatformService(owner)
                .platformServiceDescriptor(serviceDescriptor)
                .build());
    }

    private Job persistJob(String jobExtId, PlatformService service) {
        JobConfig jobConfig = jobConfigDataService.save(new JobConfig(owner, service));
        return jobDataService.save(ProcessingCoreEntities.createJob(owner, jobConfig)
                .extId(jobExtId)
                .build());
    }

    private Job persistSubJob(String jobExtId, Job parentJob) {
        return jobDataService.save(ProcessingCoreEntities.createJob(owner, parentJob.getConfig())
                .extId(jobExtId)
                .parentJob(parentJob)
                .build());
    }

    private Path createOutputFolder(Job job, String outputId) throws Exception {
        return Files.createDirectories(outputProductsBaseDir.resolve(job.getExtId()).resolve(outputId));
    }

    private Path createOutputFile(Job job, String outputId, String filename, String content) throws Exception {
        Path outputFile = createOutputFolder(job, outputId).resolve(filename);
        Files.createDirectories(outputFile.getParent());
        return Files.write(outputFile, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path createOutputFile(Job job, String outputId, String filename, String content, Instant lastModified) throws Exception {
        Path outputFile = createOutputFile(job, outputId, filename, content);
        Files.setLastModifiedTime(outputFile, FileTime.from(lastModified));
        return outputFile;
    }

    private static PlatformServiceDescriptor.Parameter createOutput(String outputId, ImmutableMap<String, String> platformMetadata) {
        return PlatformServiceDescriptor.Parameter.builder()
                .id(outputId)
                .platformMetadata(platformMetadata)
                .build();
    }

}
