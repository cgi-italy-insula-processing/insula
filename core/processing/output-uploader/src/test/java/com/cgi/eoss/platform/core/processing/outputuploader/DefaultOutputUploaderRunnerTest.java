package com.cgi.eoss.platform.core.processing.outputuploader;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DefaultOutputUploaderRunnerTest {

    private static final String JOB_ID = "JOB_ID";

    private static final Map<String, String> OUTPUTS = ImmutableMap.of("output1", "test1");

    private static final Path WORKING_DIR = Paths.get("base", "path");

    private final IngestionService ingestionService = Mockito.mock(IngestionService.class);

    private DefaultOutputUploaderRunner defaultOutputUploaderRunner;

    @Before
    public void init() {
        defaultOutputUploaderRunner = new DefaultOutputUploaderRunner(
                ingestionService,
                WORKING_DIR,
                JOB_ID,
                OUTPUTS
        );
    }

    @After
    public void after() {
        verifyNoMoreInteractions(ingestionService);
    }

    @Test
    public void testRun_ThrowsIOException_WhenIoErrorInIngestionOutputsOccurs() throws IOException {
        doThrow(new IOException("test-exception"))
                .when(ingestionService).uploadOutputs(WORKING_DIR, JOB_ID, OUTPUTS);

        Assertions.assertThatThrownBy(() -> defaultOutputUploaderRunner.run())
                .isInstanceOf(IOException.class)
                .hasMessage("test-exception");

        verify(ingestionService).uploadOutputs(WORKING_DIR, JOB_ID, OUTPUTS);
    }
}