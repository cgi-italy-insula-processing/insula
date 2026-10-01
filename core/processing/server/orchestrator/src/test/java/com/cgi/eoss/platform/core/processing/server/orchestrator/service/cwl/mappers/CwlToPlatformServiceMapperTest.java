package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.mappers;

import com.cgi.eoss.cwl_v1_2.CommandLineTool;
import com.cgi.eoss.cwl_v1_2.Process;
import com.cgi.eoss.cwl_v1_2.utils.RootLoader;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDockerBuildInfo;
import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.mappers.CwlToPlatformServiceMapper.*;
import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class CwlToPlatformServiceMapperTest {

    private static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "cwl");

    private static final String FORMAT = "format";

    private static final String CATALOGUE = "CATALOGUE";

    private static final String OTHER = "OTHER";

    private static final String TYPE = "type";

    private static final String STAC = "STAC";

    private static final String PREVENT_URL_DOWNLOAD = "preventUrlDownload";

    private PlatformService platformService;

    @Before
    public void init() {
        platformService = new PlatformService();
    }

    @Test
    public void testMapInitialWorkDirRequirementDirectoryToUserMounts_MapsFirstMapCommandLineToolInitialWorkDirRequirementsInUserMounts() {

        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        Object doc = RootLoader.loadDocument(cwlFile);
        List<Process> cwl = (List<Process>) doc;
        List<UserMount> userMounts = mapInitialWorkDirRequirementDirectoryToUserMounts(cwl);

        UserMount userMount = new UserMount();
        userMount.setMountPath("home/first");
        userMount.setName("userMount");

        assertThat(userMounts).containsExactly(
                userMount
        );
    }

    @Test
    public void testMapInitialWorkDirRequirementDirectoryToUserMounts_MapsFirstMapCommandLineToolInitialWorkDirRequirementsInUserMounts_WhenLocationDoesNotContainSlash() throws IOException {

        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        String cwlString = new String(Files.readAllBytes(cwlFile));

        Object doc = RootLoader.loadDocument(cwlString.replace("location: ./mount/userMount", "location: userMount"));
        List<Process> cwl = (List<Process>) doc;
        List<UserMount> userMounts = mapInitialWorkDirRequirementDirectoryToUserMounts(cwl);

        UserMount userMount = new UserMount();
        userMount.setMountPath("home/first");
        userMount.setName("userMount");

        assertThat(userMounts).containsExactly(
                userMount
        );
    }

    @Test
    public void testToPlatformService_MapsCwlToPlatformService() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        Object doc = RootLoader.loadDocument(cwlFile);
        List<Process> cwl = (List<Process>) doc;
        platformService = toPlatformService(cwl);
        assertThat(platformService.getName()).isEqualTo("s2-cropper");
        assertThat(platformService.getDescription()).isEqualTo("This application crops a Sentinel-2 band");
        assertThat(platformService.getDockerTag()).isEqualTo("ogc-crop:0.1");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        PlatformServiceDockerBuildInfo expectedDockerBuildInfo = new PlatformServiceDockerBuildInfo();
        expectedDockerBuildInfo.setDockerBuildStatus(PlatformServiceDockerBuildInfo.Status.COMPLETED);
        assertThat(platformService.getDockerBuildInfo()).isEqualTo(expectedDockerBuildInfo);

        // Verify required resources
        assertThat(platformService.getRequiredResources()).isEqualTo(PlatformServiceResources.builder()
                .ram("2Mi")
                .cpus("2")
                .build());

        // Verify serviceDescriptor and its input/output parameters
        PlatformServiceDescriptor actualServiceDescriptor = platformService.getServiceDescriptor();
        assertThat(actualServiceDescriptor.getId()).isEqualTo("s2-cropper");
        assertThat(actualServiceDescriptor.getTitle()).isEqualTo("Sentinel-2 band crop");
        assertThat(actualServiceDescriptor.getDescription()).isEqualTo("This application crops a Sentinel-2 band");
        assertThat(actualServiceDescriptor.getDockerCommand()).isEqualTo("crop");
        assertThat(actualServiceDescriptor.getDockerArguments()).isEqualTo(ImmutableList.of("arg1", "arg2"));
        assertThat(actualServiceDescriptor.getParallelInputsKey()).isNull();
        assertThat(actualServiceDescriptor.getDataInputs()).containsExactlyInAnyOrder(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputDirectory").title("Sentinel-2 inputs").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, TYPE, STAC, PREVENT_URL_DOWNLOAD, "false"))
                        .minOccurs(1).maxOccurs(1).description("Sentinel-2 Level-1C or Level-2A input reference")
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputStringWithDefault").title("inputStringWithDefault")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(2).prefix("--prefix").build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string", "value", "def"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("OptFloat").title("opt float")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(3).build()).description("optional float")
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .defaultAttrs(ImmutableMap.of("dataType", "double")).data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .minOccurs(0).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputEnum").title("enum").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(4).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).description("enum type")
                        .defaultAttrs(ImmutableMap.of("dataType", "string", "allowedValues", "bam,sam"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputArray").title("inputArray").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(5).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(1).maxOccurs(100)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputInt").title("inputInt").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(6).build())
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .defaultAttrs(ImmutableMap.of("dataType", "integer")).data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputLong").title("inputLong").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(7).build())
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .defaultAttrs(ImmutableMap.of("dataType", "integer")).data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputFile").title("inputFile").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(8).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "false"))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputBool").title("inputBool").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(9).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("OptArray").title("OptArray").inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(10).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(0).maxOccurs(100)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("OptEnum").title("OptEnum")
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .defaultAttrs(ImmutableMap.of("dataType", "string", "allowedValues", "vam,ram"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(0).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputStringWithoutPosition").title("inputStringWithoutPosition")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().prefix("--prefix").build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(1).maxOccurs(1)
                        .build()
        );
        assertThat(actualServiceDescriptor.getDataOutputs()).containsExactlyInAnyOrder(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("cropped_tif").title("Cropped band")
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, TYPE, STAC, PREVENT_URL_DOWNLOAD, "false"))
                        .minOccurs(1).maxOccurs(1).description("Cropped Sentinel-2 band")
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob(".").build())
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("flat_cropped_tif").title("Flat Cropped band").description("Flat Cropped Sentinel-2 band")
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, TYPE, STAC, PREVENT_URL_DOWNLOAD, "false"))
                        .minOccurs(1).maxOccurs(1)
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob(".").build())
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("file_output").title("File output").description("File output of the crop")
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "false"))
                        .minOccurs(1).maxOccurs(1)
                        .outputBinding(PlatformServiceDescriptor.OutputBinding.builder().glob("./out").build())
                        .build()
        );

        assertThat(actualServiceDescriptor.getEnvironmentVariables()).isEqualTo(
                ImmutableMap.of("PATH", "/opt/sbin:/bin")
        );
    }

    @Test
    public void testToPlatformService_MapsCwlToPlatformService_WhenCwlContainsScatterFeatureRequirements() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getName()).isEqualTo("s2-cropper");
        assertThat(platformService.getDescription()).isEqualTo("This application crops bands from a Sentinel-2 product");
        assertThat(platformService.getDockerTag()).isEqualTo("ogc-crop:0.1");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isEqualTo("band");
        PlatformServiceDescriptor.Parameter band = getInputParameterById(platformService, "band");
        assertThat(band.getMinOccurs()).isEqualTo(1);
        assertThat(band.getMaxOccurs()).isEqualTo(100);
    }

    @Test
    public void testToPlatformService_DoesNotPromoteScatterInputOccurrences_WhenScatterStepSourceDoesNotMatchWorkflowInput() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout.cwl");
        String cwlString = readAsString(cwlFile);
        cwlString = cwlString.replace("band: band", "band: DOES_NOT_EXIST");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlString);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isEqualTo("band");
        PlatformServiceDescriptor.Parameter band = getInputParameterById(platformService, "band");
        assertThat(band.getMinOccurs()).isEqualTo(1);
        assertThat(band.getMaxOccurs()).isEqualTo(1);
    }

    @Test
    public void testToPlatformService_PromotesScatterInputOccurrences_WhenWorkflowInputIsArrayAndToolInputIsScalar() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isEqualTo("band");
        PlatformServiceDescriptor.Parameter band = getInputParameterById(platformService, "band");
        assertThat(band.getMinOccurs()).isEqualTo(1);
        assertThat(band.getMaxOccurs()).isEqualTo(100);
    }

    @Test
    public void testToPlatformService_DoesNotPromoteOccurrences_WhenWorkflowScatterInputIsNotArray() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout-with-scatter-not-array.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);

        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isEqualTo("band");

        PlatformServiceDescriptor.Parameter band = getInputParameterById(platformService, "band");
        assertThat(band.getMinOccurs()).isEqualTo(1);
        assertThat(band.getMaxOccurs()).isEqualTo(1);
    }

    @Test
    public void testToPlatformService_DoesNotPromoteOccurrences_WhenCommandLineToolInputDoesNotContainParallelKey() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout-with-command-input-not-matching-scatter.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);

        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isEqualTo("band");

        assertThat(platformService.getServiceDescriptor().getDataInputs().stream()
                .anyMatch(p -> "band".equals(p.getId()))).isFalse();

        PlatformServiceDescriptor.Parameter notMatching = getInputParameterById(platformService, "notMatchingInput");
        assertThat(notMatching.getMinOccurs()).isEqualTo(1);
        assertThat(notMatching.getMaxOccurs()).isEqualTo(1);
    }

    @Test
    public void testToPlatformService_ThrowsNoSuchElementException_WhenNoMatchingScatterStepInputFound() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout-with-no-matching-scatter-step-input.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);

        assertThatThrownBy(() -> toPlatformService(cwl))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    public void testToPlatformService_DoesNotPromoteOccurrences_WhenScatterIsMissing() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout-with-missing-scatter.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);

        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);

        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isNull();

        PlatformServiceDescriptor.Parameter band = getInputParameterById(platformService, "band");
        assertThat(band.getMinOccurs()).isEqualTo(1);
        assertThat(band.getMaxOccurs()).isEqualTo(1);
    }

    @Test
    public void testToPlatformService_DoesNotPromoteOccurrences_WhenScatterStepSourceIsArray() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-fanout-with-step-source-array.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);

        platformService = toPlatformService(cwl);

        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PARALLEL_PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getParallelInputsKey()).isEqualTo("band");

        PlatformServiceDescriptor.Parameter band = getInputParameterById(platformService, "band");
        assertThat(band.getMinOccurs()).isEqualTo(1);
        assertThat(band.getMaxOccurs()).isEqualTo(1);
    }

    @Test
    public void testToPlatformService_MapsEmptyListToServiceDescriptorDockerArguments_WhenCwlDoesNotContainArgumentsField() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-with-command-line-tool-without-arguments.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getName()).isEqualTo("s2-cropper");
        assertThat(platformService.getDescription()).isEqualTo("This application crops a Sentinel-2 band");
        assertThat(platformService.getDockerTag()).isEqualTo("ogc-crop:0.1");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        assertThat(platformService.getServiceDescriptor().getDockerArguments()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testToPlatformService_MapsStorageRequestAsGiB_WhenTempDirMinResourceIsSet() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                            readAsString(cwlFile), ImmutableMap.of("tmpdirMin", 1024)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isEqualTo("1");
    }

    @Test
    public void testToPlatformService_MapsStorageRequestAsGiB_WhenOutDirMinResourceIsSet() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("outdirMin", 1024)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isEqualTo("1");
    }

    @Test
    public void testToPlatformService_MapsTotalSumToStorageRequestAsGiB_WhenTempDirMinAndOutDirResourceAreSet() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", 1024, "outdirMin", 1024)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isEqualTo("2");
    }

    @Test
    public void testToPlatformService_MapsRoundingUpStorageRequestAsGiB_WhenValueIsPartialGiB() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", 1040)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isEqualTo("2");
    }

    @Test
    public void testToPlatformService_DoesNotMapStorageRequest_WhenTotalMiBRequestedIsZero() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", 0, "outdirMin", 0)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isNull();
    }

    @Test
    public void testToPlatformService_DoesNotMapStorageRequest_WhenTotalMiBRequestedIsNegative() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", -5, "outdirMin", -2)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isNull();
    }

    @Test
    public void testToPlatformService_MapsOnlyPositiveStorageRequest_WhenOneRequestIsNegativeAndOtherPositive() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", -5, "outdirMin", 1024)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isEqualTo("1");
    }

    @Test
    public void testToPlatformService_DoesNotMapStorageRequest_WhenOnlyOneResourceIsZeroAndOtherIsMissing() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", 0)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isNull();
    }

    @Test
    public void testToPlatformService_MapsToOneGiB_WhenMinimumResourcesAreSet() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(appendResourceRequirements(
                readAsString(cwlFile), ImmutableMap.of("tmpdirMin", 1)));

        platformService = toPlatformService(cwl);

        assertThat(platformService.getRequiredResources().getStorage()).isEqualTo("1");
    }

    @Test
    public void testToPlatformService_MapsBaseCommandAndArguments_WhenAllDataIsProvided() throws IOException {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        String cwlString = readAsString(cwlFile);
        cwlString = cwlString.replace("baseCommand: crop", "baseCommand: [\"python\", \"main.py\"]");

        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlString);
        platformService = toPlatformService(cwl);

        PlatformServiceDescriptor sd = platformService.getServiceDescriptor();
        assertThat(sd.getDockerCommand()).isEqualTo("python");
        assertThat(sd.getDockerArguments()).containsExactly("main.py", "arg1", "arg2");
    }

    @Test
    public void testToPlatformService_MapsBaseCommandAndArguments_WhenArgumentsAreMissing() throws IOException {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package-with-command-line-tool-without-arguments.cwl");
        String cwlString = readAsString(cwlFile);
        cwlString = cwlString.replace("baseCommand: crop", "baseCommand: [\"python\", \"main.py\"]");

        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlString);
        platformService = toPlatformService(cwl);

        PlatformServiceDescriptor sd = platformService.getServiceDescriptor();
        assertThat(sd.getDockerCommand()).isEqualTo("python");
        assertThat(sd.getDockerArguments()).containsExactly("main.py");
    }

    @Test
    public void testToPlatformService_MapsBaseCommandAndArguments_WhenBaseCommandIsMissing() throws IOException {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        String cwlString = readAsString(cwlFile);
        cwlString = cwlString.replace("baseCommand: crop", "baseCommand: []");

        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlString);
        platformService = toPlatformService(cwl);

        PlatformServiceDescriptor sd = platformService.getServiceDescriptor();
        assertThat(sd.getDockerCommand()).isNull();
        assertThat(sd.getDockerArguments()).containsExactly("arg1", "arg2");
    }

    @Test
    public void testToPlatformService_ThrowsIllegalStateException_WhenWorkflowIsMissing() {

        List<Process> cwl = Collections.emptyList();

        assertThatThrownBy(() -> toPlatformService(cwl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No Workflow found in CWL list");
    }

    @Test
    public void testToPlatformService_ThrowsIllegalStateException_WhenCommandLineToolIsMissing() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);

        cwl.removeIf(p -> p instanceof CommandLineTool);

        assertThatThrownBy(() -> toPlatformService(cwl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No CommandLineTool found in CWL list");
    }

    @Test
    public void testToPlatformService_MapsArguments_WhenBaseCommandIsMissing() {
        Path cwlFile = BASE_TEST_PATH.resolve("app-package.cwl");
        String cwlString = readAsString(cwlFile);

        cwlString = cwlString.replace("baseCommand: crop", "");

        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlString);

        platformService = toPlatformService(cwl);

        PlatformServiceDescriptor sd = platformService.getServiceDescriptor();

        assertThat(sd.getDockerCommand()).isNull();
        assertThat(sd.getDockerArguments()).containsExactly("arg1", "arg2");
    }

    @Test
    public void testMapExtraSchemaAttributes_MapsAttributesToPlatformService() {
        String cwlAsString = readAsString(BASE_TEST_PATH.resolve("app-package.cwl"));
        platformService = new PlatformService();
        platformService.setServiceDescriptor(PlatformServiceDescriptor.builder().build());
        String cwlUrl = "https://cwl.url/path/app-package.cwl";
        mapExtraSchemaAttributes(cwlAsString, URI.create(cwlUrl), platformService);
        assertThat(platformService.getCwl()).isEqualTo(new Cwl(URI.create(cwlUrl), cwlAsString));
        assertThat(platformService.getServiceDescriptor().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    public void testMapExtraSchemaAttributes_MapsAttributesToPlatformService_WhenVersionIsNotProvided() {
        String cwlAsString = readAsString(BASE_TEST_PATH.resolve("app-package-without-version.cwl"));
        platformService = new PlatformService();
        platformService.setServiceDescriptor(PlatformServiceDescriptor.builder().build());
        String cwlUrl = "https://cwl.url/path/app-package.cwl";
        mapExtraSchemaAttributes(cwlAsString, URI.create(cwlUrl), platformService);
        assertThat(platformService.getCwl()).isEqualTo(new Cwl(URI.create(cwlUrl), cwlAsString));
        assertThat(platformService.getServiceDescriptor().getVersion()).isEqualTo("N/A");
    }

    @Test
    public void testToPlatformService_MapsOutputFileFormatAsOther_WhenCwlOutputHasNoFormatSpecified() {
        Path cwlFile = BASE_TEST_PATH.resolve("fast-copier-file.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getName()).isEqualTo("FastCopierFilesCWL");
        assertThat(platformService.getDescription()).isEqualTo("This CWL creates a service that refers to a basic StageIn+StageOut FastCopier");
        assertThat(platformService.getDockerTag()).isEqualTo("eopaas/fastcopierfiles:latest");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        PlatformServiceDescriptor.Parameter out = getOutputParameterById(platformService, "out");
        assertThat( out.getPlatformMetadata().get("format")).isEqualTo("OTHER");
    }

    @Test
    public void testToPlatformService_MapsOutputFileFormatAsGeotiff_WhenCwlOutputHasFormatGeotiff() {
        Path cwlFile = BASE_TEST_PATH.resolve("fast-copier-geotiff.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getName()).isEqualTo("FastCopierFilesCWL");
        assertThat(platformService.getDescription()).isEqualTo("This CWL creates a service that refers to a basic StageIn+StageOut FastCopier");
        assertThat(platformService.getDockerTag()).isEqualTo("eopaas/fastcopierfiles:latest");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        PlatformServiceDescriptor.Parameter out = getOutputParameterById(platformService, "out");
        assertThat( out.getPlatformMetadata().get("format")).isEqualTo("GEOTIFF");
    }

    @Test
    public void testToPlatformService_MapsOutputFileFormatAsShapefile_WhenCwlOutputHasFormatShapefile() {
        Path cwlFile = BASE_TEST_PATH.resolve("fast-copier-shapefile.cwl");
        List<Process> cwl = (List<Process>) RootLoader.loadDocument(cwlFile);
        platformService = toPlatformService(cwl);
        assertThat(platformService.getName()).isEqualTo("FastCopierFilesCWL");
        assertThat(platformService.getDescription()).isEqualTo("This CWL creates a service that refers to a basic StageIn+StageOut FastCopier");
        assertThat(platformService.getDockerTag()).isEqualTo("eopaas/fastcopierfiles:latest");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        PlatformServiceDescriptor.Parameter out = getOutputParameterById(platformService, "out");
        assertThat( out.getPlatformMetadata().get("format")).isEqualTo("SHAPEFILE");
    }

    private static PlatformServiceDescriptor.Parameter getInputParameterById(PlatformService platformService, String id) {
        return platformService.getServiceDescriptor().getDataInputs().stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing input param: " + id));
    }

    private static PlatformServiceDescriptor.Parameter getOutputParameterById(PlatformService platformService, String id) {
        return platformService.getServiceDescriptor().getDataOutputs().stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing input param: " + id));
    }

    public static String appendResourceRequirements(String yaml, Map<String, Integer> requirements) {
        String marker = "ResourceRequirement:";
        int markerIdx = yaml.indexOf(marker);
        if (markerIdx == -1) {
            throw new IllegalArgumentException(marker + " not found");
        }
        int lineEnd = yaml.indexOf('\n', markerIdx);
        lineEnd = (lineEnd == -1) ? yaml.length() : lineEnd;

        String childIndent = getChildIndent(yaml, lineEnd);
        String parentIndent = childIndent.substring(0, Math.max(0, childIndent.length() - 4));

        int insertPos = yaml.indexOf("\n" + parentIndent, lineEnd);
        insertPos = (insertPos == -1) ? yaml.length() : insertPos + 1;

        StringBuilder sb = new StringBuilder();
        requirements.forEach((k, v) -> sb.append(childIndent).append(k).append(": ").append(v).append("\n"));

        return yaml.substring(0, insertPos) + sb + yaml.substring(insertPos);
    }

    private static String getChildIndent(String yaml, int startPos) {
        int scanPos = startPos + 1;
        while (scanPos < yaml.length()) {
            int nextNL = yaml.indexOf('\n', scanPos);
            int end = (nextNL == -1) ? yaml.length() : nextNL;
            String line = yaml.substring(scanPos, end);
            if (!line.trim().isEmpty()) {
                return line.substring(0, line.indexOf(line.trim()));
            }
            scanPos = end + 1;
        }
        return "";
    }
}