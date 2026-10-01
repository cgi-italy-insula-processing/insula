package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service.StacInputsService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class StacJobInputsProcessorTest {

    private StacInputsService stacInputsService;

    private JobInputsProcessor jobInputsProcessor;

    private InOrder inOrder;

    @Before
    public void setUp() {
        stacInputsService = mock(StacInputsService.class);
        inOrder = inOrder(stacInputsService);
        jobInputsProcessor = new StacJobInputsProcessor(stacInputsService);
    }

    @After
    public void tearDown() {
        verifyNoMoreInteractions(stacInputsService);
    }

    @Test
    public void testSplitAndExplodeInputs_ReturnsExplodedStacInput_WhenInputIsStac() {
        String jobId = "jobId";
        String userName = "userName";
        String inputId = "inputId";
        JobInput jobInput = JobInput.builder()
                .id(inputId)
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("input1", "input2"))
                .build();
        JobInput explodedJobInput = JobInput.builder()
                .id(inputId)
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("input1#fragmentId", "input2#fragmentId"))
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(inputId, jobInput))
                .jobId(jobId)
                .userName(userName)
                .build();

        when(stacInputsService.explodeStacItems(jobInput, jobId, userName)).thenReturn(explodedJobInput);

        assertThat(jobInputsProcessor.splitAndExplodeInputs(jobInputs, inputId)).isEqualTo(explodedJobInput);

        inOrder.verify(stacInputsService).explodeStacItems(jobInput, jobId, userName);
    }

    @Test
    public void testSplitAndExplodeInputs_ReturnsJobInputAsItIs_WhenInputIsNotStac() {
        String inputId = "inputId";
        JobInput jobInput = JobInput.builder()
                .id(inputId)
                .type(JobInput.Type.URL)
                .values(ImmutableList.of("input1", "input2"))
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of(inputId, jobInput))
                .build();

        assertThat(jobInputsProcessor.splitAndExplodeInputs(jobInputs, inputId)).isEqualTo(jobInput);
    }

    @Test
    public void testExplodeInputs_ReturnsJobInputsWithExplodedStacInput_WhenInputIsStac() {
        String jobId = "jobId";
        String userName = "userName";
        JobInput jobInput = JobInput.builder()
                .id("inputKey")
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("http://url.stac/search?query=1"))
                .build();
        JobInput explodedJobInput = JobInput.builder()
                .id("inputKey")
                .type(JobInput.Type.STAC)
                .values(ImmutableList.of("http://url.stac/search?query=1#fragmentId"))
                .build();
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of("inputKey", jobInput))
                .jobId(jobId)
                .userName(userName)
                .build();

        when(stacInputsService.explodeStacItems(jobInput, jobId, userName)).thenReturn(explodedJobInput);

        assertThat(jobInputsProcessor.explodeInputs(jobInputs)).isEqualTo(JobInputs.builder()
                .inputs(ImmutableMap.of("inputKey", explodedJobInput))
                .jobId(jobId)
                .userName(userName)
                .build());

        inOrder.verify(stacInputsService).explodeStacItems(jobInput, jobId, userName);
    }

    @Test
    public void testExplodeInputs_ReturnsJobInputsAsItIs_WhenInputIsNotStac() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of("inputKey", JobInput.builder()
                        .id("inputKey")
                        .type(JobInput.Type.OTHER)
                        .values(ImmutableList.of("input1", "input2"))
                        .build()))
                .build();

        assertThat(jobInputsProcessor.explodeInputs(jobInputs)).isEqualTo(jobInputs);
    }

    @Test
    public void testExplodeInputs_ReturnsJobInputsAsItIs_WhenJobInputsIsEmpty() {
        assertThat(jobInputsProcessor.explodeInputs(JobInputs.builder().inputs(Collections.emptyMap()).build()))
                .isEqualTo(JobInputs.builder().inputs(Collections.emptyMap()).build());
    }

    @Test
    public void testResolveJobInputs_ReturnsSameJobInputs() {
        JobInputs jobInputs = JobInputs.builder()
                .inputs(ImmutableMap.of("inputId", JobInput.builder()
                        .id("inputId")
                        .type(JobInput.Type.URL)
                        .values(ImmutableList.of("anUriString"))
                        .build()))
                .userName("userName")
                .build();

        assertThat(jobInputsProcessor.resolveJobInputs(jobInputs)).isSameAs(jobInputs);
    }

    @Test
    public void testResolveUri_ReturnsListWithOriginalUri() {
        assertThat(jobInputsProcessor.resolveUri("anUriString", "userName"))
                .isEqualTo(Collections.singletonList("anUriString"));
    }
}