package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoreOutputProcessorTest {

    private JobOutputsRepatriationService jobOutputsRepatriationService;
    private JobDataService jobDataService;

    private InOrder inOrder;

    private CoreOutputProcessor coreOutputProcessor;

    @Before
    public void init() {
        jobOutputsRepatriationService = mock(JobOutputsRepatriationService.class);
        jobDataService = mock(JobDataService.class);

        inOrder = inOrder(jobOutputsRepatriationService, jobDataService);

        coreOutputProcessor = new CoreOutputProcessor(jobOutputsRepatriationService, jobDataService,
                Paths.get("/data/outputProducts"));
    }

    @After
    public void shutDown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOutputs_SavesTheJobWithTheOutputPathsRelativeToTheOutputProductsBaseDir_WhenJobHasNoParentJob() throws Exception {

        Job job = createJob(57L, "jobExtId");
        when(jobOutputsRepatriationService.repatriate(job)).thenReturn(ImmutableList.of(
                new RepatriatedOutput("outputIdOne", ImmutableList.of(
                        Paths.get("/data/outputProducts/jobExtId/outputIdOne/productOne.zip")))));
        when(jobDataService.save(job)).thenReturn(job);

        coreOutputProcessor.processOutputs(job);

        inOrder.verify(jobOutputsRepatriationService).repatriate(job);
        inOrder.verify(jobDataService).save(job);

        assertThat(job.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "jobExtId/outputIdOne/productOne.zip"));
    }

    @Test
    public void testProcessOutputs_SavesTheJobWithOneRelativePathPerRepatriatedFileOfEveryOutput() throws Exception {

        Job job = createJob(83L, "jobExtId");
        when(jobOutputsRepatriationService.repatriate(job)).thenReturn(ImmutableList.of(
                new RepatriatedOutput("outputIdOne", ImmutableList.of(
                        Paths.get("/data/outputProducts/jobExtId/outputIdOne/productOne.zip"),
                        Paths.get("/data/outputProducts/jobExtId/outputIdOne/nested/productTwo.tif"))),
                new RepatriatedOutput("outputIdTwo", ImmutableList.of(
                        Paths.get("/data/outputProducts/jobExtId/outputIdTwo/report.json")))));
        when(jobDataService.save(job)).thenReturn(job);

        coreOutputProcessor.processOutputs(job);

        inOrder.verify(jobOutputsRepatriationService).repatriate(job);
        inOrder.verify(jobDataService).save(job);

        assertThat(job.getOutputs()).isEqualTo(ImmutableListMultimap.of(
                "outputIdOne", "jobExtId/outputIdOne/productOne.zip",
                "outputIdOne", "jobExtId/outputIdOne/nested/productTwo.tif",
                "outputIdTwo", "jobExtId/outputIdTwo/report.json"));
    }

    @Test
    public void testProcessOutputs_UpdatesTheParentJobOutputsWithTheSavedJob_WhenJobHasParentJob() throws Exception {

        Job parentJob = createJob(99L, "parentJobExtId");
        Job job = createJob(100L, "subJobExtId");
        job.setParentJob(parentJob);
        when(jobOutputsRepatriationService.repatriate(job)).thenReturn(ImmutableList.of(
                new RepatriatedOutput("outputIdOne", ImmutableList.of(
                        Paths.get("/data/outputProducts/subJobExtId/outputIdOne/productOne.zip")))));

        Job savedJob = createJob(100L, "subJobExtId");
        savedJob.setParentJob(parentJob);
        when(jobDataService.save(job)).thenReturn(savedJob);

        coreOutputProcessor.processOutputs(job);

        inOrder.verify(jobOutputsRepatriationService).repatriate(job);
        inOrder.verify(jobDataService).save(job);
        inOrder.verify(jobDataService).updateParentJobOutputs(savedJob);

        assertThat(job.getOutputs())
                .isEqualTo(ImmutableListMultimap.of("outputIdOne", "subJobExtId/outputIdOne/productOne.zip"));
    }

    @Test
    public void testProcessOutputs_SavesTheJobWithEmptyOutputs_WhenNoOutputIsRepatriated() throws Exception {

        Job job = createJob(131L, "jobExtId");
        when(jobOutputsRepatriationService.repatriate(job)).thenReturn(Collections.emptyList());
        when(jobDataService.save(job)).thenReturn(job);

        coreOutputProcessor.processOutputs(job);

        inOrder.verify(jobOutputsRepatriationService).repatriate(job);
        inOrder.verify(jobDataService).save(job);

        assertThat(job.getOutputs()).isEqualTo(ImmutableListMultimap.of());
    }

    @Test
    public void testProcessOutputs_ThrowsIOExceptionAndDoesNotSaveTheJob_WhenRepatriationFails() throws Exception {

        Job job = createJob(149L, "jobExtId");
        when(jobOutputsRepatriationService.repatriate(job)).thenThrow(new IOException("Object storage not reachable"));

        assertThatThrownBy(() -> coreOutputProcessor.processOutputs(job))
                .isInstanceOf(IOException.class)
                .hasMessage("Object storage not reachable");

        inOrder.verify(jobOutputsRepatriationService).repatriate(job);

        assertThat(job.getOutputs()).isNull();
    }

    private static Job createJob(Long jobId, String jobExtId) {
        Job job = new Job();
        job.setId(jobId);
        job.setExtId(jobExtId);
        return job;
    }

}
