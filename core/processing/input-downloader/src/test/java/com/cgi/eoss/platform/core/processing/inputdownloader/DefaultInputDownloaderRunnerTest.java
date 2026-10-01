package com.cgi.eoss.platform.core.processing.inputdownloader;


import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DefaultInputDownloaderRunnerTest {

    private static final String JOB_ID = "JOB_ID";

    private static final String JOB_OWNER= "JOB_OWNER";

    private static final Path BASE_PATH = Paths.get("base", "path");

    private final EnvironmentService environmentService = Mockito.mock(EnvironmentService.class);

    private DefaultInputDownloaderRunner inputDownloaderRunner;

    @Before
    public void init() {
        inputDownloaderRunner = new DefaultInputDownloaderRunner(
                environmentService,
                BASE_PATH,
                JOB_ID,
                JOB_OWNER
        );
    }

    @After
    public void shutdown() {
        verifyNoMoreInteractions(environmentService);
    }

    @Test
    public void testRun_ThrowsIOException_WhenIOErrorInPreparingEnvironmentOccurs() throws Exception {
        IOException exception = new IOException("test exception");
        doThrow(exception).when(environmentService).prepareEnvironment(JOB_ID, BASE_PATH, JOB_OWNER);

        Assertions.assertThatThrownBy(() -> inputDownloaderRunner.run())
                .isInstanceOf(IOException.class)
                .hasMessage("test exception");

        verify(environmentService).prepareEnvironment(JOB_ID, BASE_PATH, JOB_OWNER);
    }


}
