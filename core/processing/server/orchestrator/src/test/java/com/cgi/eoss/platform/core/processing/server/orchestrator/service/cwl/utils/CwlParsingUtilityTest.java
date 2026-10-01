package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils;

import com.cgi.eoss.cwl_v1_2.*;
import com.cgi.eoss.cwl_v1_2.Process;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.cgi.eoss.cwl_v1_2.utils.RootLoader;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils.CwlParsingUtility.*;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils.CwlParsingUtility.getFirstWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CwlParsingUtilityTest {

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
    public void testGetFirstWorkflow_ReturnsWorkflow_WhenCwlContainsOneWorkflow() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        String cwlFileBaseUri = String.valueOf(cwlFile.toUri());
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        assertThat(workflow.getId().get()).isEqualTo(cwlFileBaseUri + "#s2-cropper");
        assertThat(workflow.getLabel().get()).isEqualTo("Sentinel-2 band crop");
        assertThat(workflow.getDoc()).isEqualTo("This application crops a Sentinel-2 band");
    }

    @Test
    public void testGetFirstCommandLineTool_ReturnsCommandLineTool_WhenCwlContainsOneCommandLineTool() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        String cwlFileBaseUri = String.valueOf(cwlFile.toUri());
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        CommandLineTool commandLineTool = getFirstCommandLineTool(cwl).get();
        assertThat(commandLineTool.getId().get()).isEqualTo( cwlFileBaseUri + "#crop-cl");
        assertThat(commandLineTool.getBaseCommand()).isEqualTo("crop");
        assertThat(commandLineTool.getArguments().get()).containsExactlyInAnyOrder("arg1", "arg2");
    }

    @Test
    public void testGetRequirements_ReturnsListOfRequirements_WhenCommandLineToolContainsRequirements() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        List<Object> requirements = getRequirements(getFirstCommandLineTool(cwl).get());
        assertThat(requirements.size()).isEqualTo( 5);
        assertThat(requirements).hasOnlyElementsOfTypes(InitialWorkDirRequirement.class, DockerRequirementImpl.class,
                ResourceRequirementImpl.class, NetworkAccessImpl.class, EnvVarRequirementImpl.class);
    }

    @Test
    public void testGetRequirements_ReturnsListOfRequirements_WhenWorkflowContainsRequirements() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        List<Object> requirements = getRequirements(getFirstWorkflow(cwl).get());
        assertThat(requirements.size()).isEqualTo( 1);
        assertThat((requirements.get(0))).isInstanceOf(ScatterFeatureRequirement.class);
    }

    @Test
    public void testGetIdentifierFromFragment_ReturnsParameterIdentifier_WhenFragmentHasNoPath() {
        String parameterUrl = "https://some.url/with/path/app-package.cwl#parameterId";
        assertThat(getIdentifierFromFragment(parameterUrl)).isEqualTo("parameterId");
    }

    @Test
    public void testGetIdentifierFromFragment_ReturnsParameterIdentifier_WhenFragmentHasPath() {
        String parameterUrl = "https://some.url/with/path/app-package.cwl#path/to/parameterId";
        assertThat(getIdentifierFromFragment(parameterUrl)).isEqualTo("parameterId");
    }

    @Test
    public void testGetFirstWorkflow_ReturnsEmptyOptional_WhenCwlDoesNotContainWorkflows() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-without-workflow.cwl"),
                this.fs.getPath( "app-package-without-workflow.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(getFirstWorkflow(cwl)).isEmpty();

    }

    @Test
    public void testGetFirstCommandLineTool_ReturnsEmptyOptional_WhenCwlDoesNotContainCommandLineTools() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-without-command-line-tool.cwl"),
                this.fs.getPath( "app-package-without-command-line-tool.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(getFirstCommandLineTool(cwl)).isEmpty();
    }

    @Test
    public void testAsString_ReturnsCWLFullIdentifierFileUri() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        String cwlFileBaseUri = String.valueOf(cwlFile.toUri());
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(asString(cwl)).isEqualTo(cwlFileBaseUri);
    }

    @Test
    public void testAsString_ReturnsWorkflowID() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(asString(getFirstWorkflow(cwl).get())).isEqualTo("s2-cropper");
    }

    @Test
    public void testAsString_ReturnsWorkflowStepID() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(asString((WorkflowStep) getFirstWorkflow(cwl).get().getSteps().get(0))).isEqualTo("node_crop");
    }

    @Test
    public void testAsString_ReturnsCommandLineToolID() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        assertThat(asString(getFirstCommandLineTool(cwl).get())).isEqualTo("crop-cl");
    }

    @Test
    public void testIsArrayType_ReturnsTrue_WhenInputIsArray() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout.cwl"),
                this.fs.getPath( "app-package-fanout.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        WorkflowInputParameter parameter = (WorkflowInputParameter) workflow.getInputs().get(0);
        assertThat(isArrayType(parameter.getType().getClass())).isTrue();
    }

    @Test
    public void testIsArrayType_ReturnsFalse_WhenInputIsNotArray() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"),
                this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        WorkflowInputParameter parameter = (WorkflowInputParameter) workflow.getInputs().get(0);
        assertThat(isArrayType(parameter.getType().getClass())).isFalse();
    }

    @Test
    public void testGetTypeFromArray_ReturnsCWLType_WhenInputIsArray() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout.cwl"),
                this.fs.getPath( "app-package-fanout.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        WorkflowInputParameter parameter = (WorkflowInputParameter) workflow.getInputs().get(0);
        assertThat(getTypeFromArray((ArraySchema) parameter.getType())).isEqualTo(CWLType.STRING);
    }

    @Test
    public void testIsScatter_ReturnsTrue_WhenCwlContainsScatterFeatureRequirement() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package-fanout.cwl"),
                this.fs.getPath( "app-package-fanout.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        assertThat(isScatter(workflow)).isTrue();
    }

    @Test
    public void testIsScatter_ReturnsFalse_WhenCwlDoesNotContainScatterFeatureRequirement() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"),
                this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        assertThat(isScatter(workflow)).isFalse();
    }

    @Test
    public void testFindMatchingWorkflowStepInput_ReturnsMatchingWorkflowStepInput() throws Exception {
        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"),
                this.fs.getPath( "app-package.cwl"));
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        Workflow workflow = getFirstWorkflow(cwl).get();
        WorkflowStep workflowStep = (WorkflowStep) workflow.getSteps().get(0);
        CommandLineTool commandLineTool = getFirstCommandLineTool(cwl).get();
        CommandInputParameter commandInputParameter = (CommandInputParameter) commandLineTool.getInputs().get(0);
        assertThat(findMatchingWorkflowStepInput(commandInputParameter.getId().get(),
                workflowStep.getIn()).get().getId().get()).contains("app-package.cwl#s2-cropper/node_crop/OptArray");
    }

    @Test
    public void testExtractDirectoryListings_ReturnsAStreamOfDirectoriesList() throws IOException {

        Path cwlFile = Files.copy(BASE_TEST_PATH.resolve("app-package.cwl"), this.fs.getPath( "app-package.cwl"));
        String cwlFileBaseUri = String.valueOf(cwlFile.toUri());
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        CommandLineTool commandLineTool = getFirstCommandLineTool(cwl).get();

        List<Object> requirements = getRequirements(commandLineTool);
        Stream<InitialWorkDirRequirement> initialWorkDirRequirementStream = requirements.stream().filter(InitialWorkDirRequirement.class::isInstance).map(processRequirement -> (InitialWorkDirRequirement) processRequirement);
        Stream<List<Directory>> listStream = extractDirectoryListings(initialWorkDirRequirementStream);

        List<List<Directory>> listing = listStream.collect(Collectors.toList());
        assertThat(listing).hasSize(1);

        List<Directory> directories = listing.get(0);
        assertThat(listing).hasSize(1);

        Directory directory = directories.get(0);
        assertThat(directory.getBasename().get()).isEqualTo("home/first");
        assertThat(directory.getLocation().get()).endsWith("/mount/userMount");
    }

    @Test
    public void testExtractDirectoryListings_ReturnsAnEmptyStreamOfDirectoriesList_WhenInpuStreamIsEmpty() {
        Stream<InitialWorkDirRequirement> emptyStream = (new ArrayList<InitialWorkDirRequirement>()).stream();
        Stream<List<Directory>> listStream = extractDirectoryListings(emptyStream);

        List<List<Directory>> listing = listStream.collect(Collectors.toList());
        assertThat(listing).hasSize(0);
    }

    @Test
    public void testExtractDirectoryListings_ThrowsNullPointerException_WhenInputStreamIsNull() {
        assertThatThrownBy(()->extractDirectoryListings(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testGetSubstringAfterLastSeparator_ReturnsTheSubstring(){
        assertThat(getSubstringAfterLastSeparator("string1/string2",'/'))
                .isEqualTo("string2");
    }

    @Test
    public void testGetSubstringAfterLastSeparator_ReturnsTheSubstring_WhereThereAreMultipleSeparator(){
        assertThat(getSubstringAfterLastSeparator("string1#string2#string3",'#'))
                .isEqualTo("string3");
    }

    @Test
    public void testGetSubstringAfterLastSeparator_ReturnsSourceString_WhenInputIsNull(){
        assertThat(getSubstringAfterLastSeparator(null,'#'))
                .isNull();
    }

    @Test
    public void testGetSubstringAfterLastSeparator_ReturnsSourceString_WhenInputIsEmpty(){
        assertThat(getSubstringAfterLastSeparator("",'#'))
                .isEmpty();
    }

    @Test
    public void testGetSubstringAfterLastSeparator_ReturnsSourceString_WhenSeparatorMiss(){
        assertThat(getSubstringAfterLastSeparator("string1#string2#string3",'/'))
                .isEqualTo("string1#string2#string3");
    }
}
