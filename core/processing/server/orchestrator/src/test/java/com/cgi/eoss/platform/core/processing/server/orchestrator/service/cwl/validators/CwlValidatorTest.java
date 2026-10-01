package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.validators;

import com.cgi.eoss.cwl_v1_2.Process;
import com.cgi.eoss.cwl_v1_2.utils.RootLoader;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.validators.CwlValidator.validate;
import static org.assertj.core.api.Assertions.assertThat;

public class CwlValidatorTest {

    private final static Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "cwl");

    private FileSystem fs;

    @Before
    public void init() throws Exception {
        this.fs = Jimfs.newFileSystem(Configuration.unix());
    }

    @After
    public void shutdown() throws Exception {
        this.fs.close();
    }

    @Test
    public void testValidate_ReturnsValidCwlValidationResult_WhenCwlIsValid() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"),
                this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(true).build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenInitialWorkDirRequirementDirectoryLocationIsNull() throws Exception {
        String resourceFileName = "app-package-not-valid-directory-location-null.cwl";
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve(resourceFileName),
                this.fs.getPath( resourceFileName));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().validationMessage("InitialWorkDirRequirement Directory in CommandLineTool crop-cl must have a defined Basename and Location and not null").isValid(false).build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenInitialWorkDirRequirementDirectoryLocationIsEmpty() throws Exception {
        String resourceFileName = "app-package-not-valid-directory-location-empty.cwl";
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve(resourceFileName),
                this.fs.getPath( resourceFileName));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().validationMessage("InitialWorkDirRequirement Directory in CommandLineTool crop-cl must have a defined Basename and Location").isValid(false).build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenInitialWorkDirRequirementDirectoryBaseNameIsEmpty() throws Exception {
        String resourceFileName = "app-package-not-valid-directory-basename-empty.cwl";
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve(resourceFileName),
                this.fs.getPath( resourceFileName));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().validationMessage("InitialWorkDirRequirement Directory in CommandLineTool crop-cl must have a defined Basename and Location").isValid(false).build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenInitialWorkDirRequirementDirectoryBaseNameIsNull() throws Exception {
        String resourceFileName = "app-package-not-valid-directory-basename-null.cwl";
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve(resourceFileName),
                this.fs.getPath( resourceFileName));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().validationMessage("InitialWorkDirRequirement Directory in CommandLineTool crop-cl must have a defined Basename and Location and not null").isValid(false).build());
    }

    @Test
    public void testValidate_ReturnsValidCwlValidationResult_WhenFanoutCwlIsValid() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout.cwl"),
                this.fs.getPath( "app-package-fanout.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(true).build());
    }

    @Test
    public void testValidate_ReturnsValidCwlValidationResult_WhenCommandLineToolDoesNotContainArgumentsField() throws IOException {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-command-line-tool-without-arguments.cwl"),
                this.fs.getPath( "app-package-with-command-line-tool-without-arguments.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl).isValid()).isTrue();
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenFanoutCwlContainsInvalidScatter() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-with-invalid-scatter.cwl"),
                this.fs.getPath( "app-package-fanout-with-invalid-scatter.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Scatter input nonexisting defined in WorkflowStep not found in WorkflowStep inputs.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenFanoutCwlWorkflowInputsDoNotContainScatterInput() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-with-workflow-inputs-without-scatter.cwl"),
                this.fs.getPath( "app-package-fanout-with-workflow-inputs-without-scatter.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Scatter input band defined in WorkflowStep not found in Workflow inputs.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenFanoutCwlScatterInputIsNotArray() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-with-scatter-not-array.cwl"),
                this.fs.getPath( "app-package-fanout-with-scatter-not-array.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Scatter input band defined in WorkflowStep is not of type array.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenFanoutCwlScatterMethodIsNotPresent() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-without-scatter-method.cwl"),
                this.fs.getPath( "app-package-fanout-without-scatter-method.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("No ScatterMethod defined in WorkflowStep node_crop").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenFanoutCwlScatterMethodIsNotSupported() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-with-unsupported-scatter-method.cwl"),
                this.fs.getPath( "app-package-fanout-with-unsupported-scatter-method.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Unsupported ScatterMethod NESTED_CROSSPRODUCT defined in WorkflowStep node_crop").build());
    }


    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCommandLineToolInputDoesNotMatchScatterInput() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-with-command-input-not-matching-scatter.cwl"),
                this.fs.getPath( "app-package-fanout-with-command-input-not-matching-scatter.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Scatter input band defined in WorkflowStep not found in CommandLineTool inputs.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenFanoutCwlOutputTypeIsNotArray() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout-without-output-array.cwl"),
                this.fs.getPath( "app-package-fanout-without-output-array.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Workflow has no array output defined.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlContainsNoWorkflow() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-without-workflow.cwl"),
                this.fs.getPath( "app-package-without-workflow.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("CWL Missing Workflow has an unexpected number of Workflows: it must contain exactly one.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlWorkflowContainsMoreThanOneWorkflowSteps() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-two-workflow-steps.cwl"),
                this.fs.getPath( "app-package-with-two-workflow-steps.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("CWL Workflow s2-cropper is not valid as it contains 2 steps: it must contain exactly 1.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlContainsNoCommandLineTool() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-without-command-line-tool.cwl"),
                this.fs.getPath( "app-package-without-command-line-tool.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("CWL " + cwlFile.toUri() + " has an unexpected number of CommandLineTools: it must contain exactly one.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlWorkflowStepDoesNotReferenceCommandLineTool() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-step-not-referencing-command-line-tool.cwl"),
                this.fs.getPath( "app-package-with-step-not-referencing-command-line-tool.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("WorkflowStep node_crop does not reference CommandLineTool crop-cl").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlCommandLineToolDoesNotContainDockerRequirement() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-command-line-tool-without-docker-requirement.cwl"),
                this.fs.getPath( "app-package-with-command-line-tool-without-docker-requirement.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Found 0 DockerRequirements in CommandLineTool crop-cl: it must contain exactly one.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlCommandLineToolDoesNotContainAnyRequirement() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-command-line-tool-without-requirements.cwl"),
                this.fs.getPath( "app-package-with-command-line-tool-without-requirements.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Found 0 DockerRequirements in CommandLineTool crop-cl: it must contain exactly one.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlCommandLineToolContainsUnsupportedRequirement() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-command-line-tool-with-unsupported-requirements.cwl"),
                this.fs.getPath( "app-package-with-command-line-tool-with-unsupported-requirements.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("CommandLineTool crop-cl contains unsupported requirements.").build());
    }


    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenCwlCommandLineToolDockerRequirementDoesNotContainDockerPull() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-command-line-tool-with-docker-requirement-without-docker-pull.cwl"),
                this.fs.getPath( "app-package-with-command-line-tool-with-docker-requirement-without-docker-pull.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("DockerRequirement in CommandLineTool crop-cl does not contain a docker pull.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenWorkflowStepAndWorkflowInputsDoNotMatch() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-workflow-steps-and-workflow-inputs-do-not-match.cwl"),
                this.fs.getPath( "app-package-workflow-steps-and-workflow-inputs-do-not-match.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("WorkflowStep and Workflow inputs do not match.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenWorkflowStepAndCommandLineToolInputsDoNotMatch() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-workflow-steps-and-command-line-tool-inputs-do-not-match.cwl"),
                this.fs.getPath( "app-package-workflow-steps-and-command-line-tool-inputs-do-not-match.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("WorkflowStep and CommandLineTool inputs do not match.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenWorkflowStepAndWorkflowOutputsDoNotMatch() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-workflow-steps-and-workflow-outputs-do-not-match.cwl"),
                this.fs.getPath( "app-package-workflow-steps-and-workflow-outputs-do-not-match.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("WorkflowStep and Workflow outputs do not match.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenWorkflowStepAndCommandLineToolOutputsDoNotMatch() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-workflow-steps-and-command-line-tool-outputs-do-not-match.cwl"),
                this.fs.getPath( "app-package-workflow-steps-and-command-line-tool-outputs-do-not-match.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("WorkflowStep and CommandLineTool outputs do not match.").build());
    }

    @Test
    public void testValidate_ReturnsNotValidCwlValidationResult_WhenWorkflowAndCommandLineToolContainUnsupportedOutputType() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-with-unsupported-output-type.cwl"),
                this.fs.getPath( "app-package-with-unsupported-output-type.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(validate(cwl)).isEqualTo(CwlValidationResult.builder().isValid(false)
                .validationMessage("Unsupported output type in CommandLineTool and Workflow.").build());
    }
}
