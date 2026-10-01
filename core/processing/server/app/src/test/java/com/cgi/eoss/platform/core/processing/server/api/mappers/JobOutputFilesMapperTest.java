package com.cgi.eoss.platform.core.processing.server.api.mappers;

import com.cgi.eoss.platform.core.processing.server.api.resources.JobOutputFileResource;
import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.persistence.service.JobDataService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.hateoas.Link;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobOutputFilesMapperTest {

    private JobDataService jobDataService;

    private InOrder inOrder;

    private JobOutputFilesMapper jobOutputFilesMapper;

    @Before
    public void init() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("insula-test");
        request.setServerPort(8443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        jobDataService = mock(JobDataService.class);
        inOrder = inOrder(jobDataService);

        jobOutputFilesMapper = new JobOutputFilesMapper(jobDataService, "/api");
    }

    @After
    public void shutdown() {
        RequestContextHolder.resetRequestAttributes();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testToJobOutputFileResources_MapsNoOutputFile_WhenJobHasNoOutputs() {

        Job job = createJob(57L, "jobExtId", null);

        assertThat(jobOutputFilesMapper.toJobOutputFileResources(job)).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testToJobOutputFileResources_MapsEveryOutputFileToAResourceLinkedToItsDownloadEndpoint() {

        Job job = createJob(83L, "jobExtId", ImmutableListMultimap.of(
                "outputIdOne", "jobExtId/outputIdOne/productOne.zip",
                "outputIdOne", "jobExtId/outputIdOne/nested/productTwo.tif",
                "outputIdTwo", "jobExtId/outputIdTwo/report.json"));

        List<JobOutputFileResource> jobOutputFileResources = jobOutputFilesMapper.toJobOutputFileResources(job);

        assertThat(jobOutputFileResources).isEqualTo(ImmutableList.of(
                new JobOutputFileResource("productOne.zip").add(Link.of(
                        "http://insula-test:8443/api/jobs/83/outputs/outputIdOne?filename=productOne.zip", "download")),
                new JobOutputFileResource("nested/productTwo.tif").add(Link.of(
                        "http://insula-test:8443/api/jobs/83/outputs/outputIdOne?filename=nested%2FproductTwo.tif", "download")),
                new JobOutputFileResource("report.json").add(Link.of(
                        "http://insula-test:8443/api/jobs/83/outputs/outputIdTwo?filename=report.json", "download"))));
    }

    @Test
    public void testToJobOutputFileResources_PercentEncodesTheFileNameInTheDownloadLink_WhenFileNameContainsReservedCharacters() {

        Job job = createJob(131L, "jobExtId", ImmutableListMultimap.of("outputId", "jobExtId/outputId/a b&c=d+e/f.tif"));

        List<JobOutputFileResource> jobOutputFileResources = jobOutputFilesMapper.toJobOutputFileResources(job);

        assertThat(jobOutputFileResources).isEqualTo(ImmutableList.of(
                new JobOutputFileResource("a b&c=d+e/f.tif").add(Link.of(
                        "http://insula-test:8443/api/jobs/131/outputs/outputId?filename=a%20b%26c%3Dd%2Be%2Ff.tif", "download"))));
    }

    @Test
    public void testToJobOutputFileResources_MapsNoOutputFile_WhenJobIsParentWithoutSubJobs() {

        Job parentJob = createParentJob(97L, "parentJobExtId", null);
        when(jobDataService.getSubJobIds(parentJob)).thenReturn(Collections.emptyList());

        List<JobOutputFileResource> jobOutputFileResources = jobOutputFilesMapper.toJobOutputFileResources(parentJob);

        inOrder.verify(jobDataService).getSubJobIds(parentJob);

        assertThat(jobOutputFileResources).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testToJobOutputFileResources_MapsTheOutputFilesOfTheSubJobsLinkedToTheSubJobThatProducedThem_WhenJobIsParent() {

        Job parentJob = createParentJob(99L, "parentJobExtId", ImmutableListMultimap.of(
                "outputId", "subJobAExtId/outputId/a.tif",
                "outputId", "subJobBExtId/outputId/b.tif"));
        when(jobDataService.getSubJobIds(parentJob)).thenReturn(ImmutableList.of(101L, 102L));
        when(jobDataService.findByIds(ImmutableList.of(101L, 102L))).thenReturn(ImmutableList.of(
                createJob(101L, "subJobAExtId", ImmutableListMultimap.of("outputId", "subJobAExtId/outputId/a.tif")),
                createJob(102L, "subJobBExtId", ImmutableListMultimap.of("outputId", "subJobBExtId/outputId/b.tif"))));

        List<JobOutputFileResource> jobOutputFileResources = jobOutputFilesMapper.toJobOutputFileResources(parentJob);

        inOrder.verify(jobDataService).getSubJobIds(parentJob);
        inOrder.verify(jobDataService).findByIds(ImmutableList.of(101L, 102L));

        assertThat(jobOutputFileResources).isEqualTo(ImmutableList.of(
                new JobOutputFileResource("a.tif").add(Link.of(
                        "http://insula-test:8443/api/jobs/101/outputs/outputId?filename=a.tif", "download")),
                new JobOutputFileResource("b.tif").add(Link.of(
                        "http://insula-test:8443/api/jobs/102/outputs/outputId?filename=b.tif", "download"))));
    }

    @Test
    public void testToJobOutputFileResources_ThrowsIllegalStateException_WhenOutputPathDoesNotMatchTheOutputsLayout() {

        Job job = createJob(149L, "jobExtId", ImmutableListMultimap.of("outputIdOne", "jobExtId/anotherOutputId/productOne.zip"));

        try {
            jobOutputFilesMapper.toJobOutputFileResources(job);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("Output path jobExtId/anotherOutputId/productOne.zip of job 149 does not match"
                    + " the layout jobExtId/outputIdOne/<filename>");
        }
    }

    @Test
    public void testToJobOutputFileResources_ThrowsIllegalStateException_WhenOutputPathHoldsNoFileName() {

        Job job = createJob(151L, "jobExtId", ImmutableListMultimap.of("outputIdOne", "jobExtId/outputIdOne/"));

        try {
            jobOutputFilesMapper.toJobOutputFileResources(job);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("Output path jobExtId/outputIdOne/ of job 151 does not match"
                    + " the layout jobExtId/outputIdOne/<filename>");
        }
    }

    private static Job createJob(Long jobId, String jobExtId, Multimap<String, String> outputs) {
        Job job = new Job();
        job.setId(jobId);
        job.setExtId(jobExtId);
        job.setOutputs(outputs);
        return job;
    }

    private static Job createParentJob(Long jobId, String jobExtId, Multimap<String, String> outputs) {
        Job parentJob = createJob(jobId, jobExtId, outputs);
        parentJob.setParent(true);
        return parentJob;
    }

}
