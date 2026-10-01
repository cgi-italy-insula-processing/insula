package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.download.Subsetting;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.core.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.times;

public class EnvironmentServiceTest {

    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new GuavaModule());

    private final DownloaderService downloaderService = Mockito.mock(DownloaderService.class);

    private final StageInService stageInService = Mockito.mock(StageInService.class);

    private final InOrder inOrder = Mockito.inOrder(stageInService, downloaderService);

    private FileSystem fileSystem;

    private Path inputDirectory;

    private static final String JOB_INPUTS_JSON_FILENAME = "jobInputs.json";
    private static final String USER_UUID = "owner";

    @Before
    public void init() throws IOException {
        fileSystem = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("basic", "owner", "posix", "unix").build());
        inputDirectory = fileSystem.getPath("/tmp");

        FilesUtils.deleteDirContentsIfExists(inputDirectory);
        Files.createDirectory(inputDirectory);
    }

    @After
    public void shutdown() throws IOException {
        try {
            FilesUtils.deleteDirContentsIfExists(inputDirectory);
        } finally {
            fileSystem.close();
        }
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testPrepareEnvironment_CreatesFoldersAndConfigurationFilesAndRequestsDownloadOfDownloadableJobParams_WhenSubsettingIsNotProvided() throws IOException {

        EnvironmentService environmentService = createEnvironmentService("{\"param1\": {\"type\": \"URL\", \"values\": [\"paramValue1_1\", \"paramValue1_2\"]}, \"param2\": {\"type\": \"OTHER\", \"values\": [\"paramValue2_1\", \"paramValue2_2\"]}}");

        environmentService.prepareEnvironment("42", inputDirectory, USER_UUID);

        List<K8sJobParams.DownloadableParam> downloadableParams = new ArrayList<>();

        K8sJobParams.DownloadableParam downloadableParam = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Arrays.asList("paramValue1_1", "paramValue1_2"))
                .build();
        downloadableParams.add(downloadableParam);

        inOrder.verify(downloaderService, times(1)).downloadInputs(
                inputDirectory.resolve("inDir"), downloadableParams, USER_UUID);

        assertThat(FilesUtils.walk(inputDirectory)).containsExactlyInAnyOrder(
                fileSystem.getPath("/tmp"),
                fileSystem.getPath("/tmp/FSTEP-WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/jobInputs.json"),
                fileSystem.getPath("/tmp/inDir"),
                fileSystem.getPath("/tmp/outDir"),
                fileSystem.getPath("/tmp/persistent")
        );

        assertThat(Files.getPosixFilePermissions(inputDirectory.resolve("outDir"))).isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));

        assertThat(StringUtils.readAsString(inputDirectory.resolve("WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n" +
                        "param2=\"paramValue2_1,paramValue2_2\"\n"
        );

        assertThat(StringUtils.readAsString(inputDirectory.resolve("FSTEP-WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n" +
                        "param2=\"paramValue2_1,paramValue2_2\"\n"
        );
    }

    @Test
    public void testPrepareEnvironment_CreatesFoldersAndConfigurationFilesAndRequestsDownloadOfDownloadableJobParamsWithSubsettingProvided() throws IOException {

        EnvironmentService environmentService = createEnvironmentService("{\"param1\": {\"type\": \"URL\", \"values\": [\"paramValue1_1\", \"paramValue1_2\"],\"subsetting\":{\"aoi\":\"aoi\",\"format\":\"format\"}}," +
                "\"param2\": {\"type\": \"OTHER\", \"values\": [\"paramValue2_1\", \"paramValue2_2\"]}}");

        environmentService.prepareEnvironment("42", inputDirectory, USER_UUID);

        List<K8sJobParams.DownloadableParam> downloadableParams = new ArrayList<>();
        K8sJobParams.DownloadableParam downloadableParam = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Arrays.asList("paramValue1_1", "paramValue1_2"))
                .subsetting(Subsetting.builder()
                        .aoi("aoi")
                        .format("format")
                        .build())
                .build();
        downloadableParams.add(downloadableParam);

        inOrder.verify(downloaderService, times(1)).downloadInputs(
                inputDirectory.resolve("inDir"), downloadableParams, USER_UUID);

        assertThat(FilesUtils.walk(inputDirectory)).containsExactlyInAnyOrder(
                fileSystem.getPath("/tmp"),
                fileSystem.getPath("/tmp/FSTEP-WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/jobInputs.json"),
                fileSystem.getPath("/tmp/inDir"),
                fileSystem.getPath("/tmp/outDir"),
                fileSystem.getPath("/tmp/persistent")
        );

        assertThat(Files.getPosixFilePermissions(inputDirectory.resolve("outDir"))).isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));

        assertThat(StringUtils.readAsString(inputDirectory.resolve("WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n" +
                        "param2=\"paramValue2_1,paramValue2_2\"\n"
        );

        assertThat(StringUtils.readAsString(inputDirectory.resolve("FSTEP-WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n" +
                        "param2=\"paramValue2_1,paramValue2_2\"\n"
        );
    }

    @Test
    public void testPrepareEnvironment_CreatesFoldersAndConfigurationFilesAndRequestStageIn_WhenStacParametersArePresent() throws IOException {
        EnvironmentService environmentService = createEnvironmentService("{\"param1\": {\"type\": \"STAC\", \"values\": [\"paramValue1_1\", \"paramValue1_2\"]}}");

        environmentService.prepareEnvironment("42", inputDirectory, USER_UUID);

        Multimap<String, String> stacParams = ArrayListMultimap.create();
        stacParams.put("param1", "paramValue1_1");
        stacParams.put("param1", "paramValue1_2");
        inOrder.verify(stageInService, times(1)).stageInInputs(
                inputDirectory.resolve("stageIn"), inputDirectory.resolve("inDir"), stacParams, USER_UUID);

        inOrder.verify(downloaderService, times(1)).downloadInputs(
                inputDirectory.resolve("inDir"), new ArrayList<>(), USER_UUID);

        assertThat(FilesUtils.walk(inputDirectory)).containsExactlyInAnyOrder(
                fileSystem.getPath("/tmp"),
                fileSystem.getPath("/tmp/FSTEP-WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/jobInputs.json"),
                fileSystem.getPath("/tmp/inDir"),
                fileSystem.getPath("/tmp/outDir"),
                fileSystem.getPath("/tmp/persistent"),
                fileSystem.getPath("/tmp/stageIn")
        );

        assertThat(Files.getPosixFilePermissions(inputDirectory.resolve("outDir"))).isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));

        assertThat(StringUtils.readAsString(inputDirectory.resolve("WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n"
        );

        assertThat(StringUtils.readAsString(inputDirectory.resolve("FSTEP-WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n"
        );
    }

    @Test
    public void testPrepareEnvironment_CreatesJobInputsJson_WhenInputsAreProvided() throws Exception {

        EnvironmentService environmentService = createEnvironmentService("{\"param1\": {\"type\": \"URL\", \"values\": [\"paramValue1_1\", \"paramValue1_2\"]}, \"param2\": {\"type\": \"OTHER\", \"values\": [\"paramValue2_1\", \"paramValue2_2\"]}}");

        environmentService.prepareEnvironment("42", inputDirectory, USER_UUID);

        List<K8sJobParams.DownloadableParam> downloadableParams = new ArrayList<>();

        K8sJobParams.DownloadableParam downloadableParam = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Arrays.asList("paramValue1_1", "paramValue1_2"))
                .build();
        downloadableParams.add(downloadableParam);

        inOrder.verify(downloaderService, times(1)).downloadInputs(
                inputDirectory.resolve("inDir"), downloadableParams, USER_UUID);

        assertThat(FilesUtils.walk(inputDirectory)).containsExactlyInAnyOrder(
                fileSystem.getPath("/tmp"),
                fileSystem.getPath("/tmp/FSTEP-WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/jobInputs.json"),
                fileSystem.getPath("/tmp/inDir"),
                fileSystem.getPath("/tmp/outDir"),
                fileSystem.getPath("/tmp/persistent")
        );

        assertThat(Files.getPosixFilePermissions(inputDirectory.resolve("outDir"))).isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));

        assertThat(StringUtils.readAsString(inputDirectory.resolve("WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n" +
                        "param2=\"paramValue2_1,paramValue2_2\"\n"
        );

        assertThat(StringUtils.readAsString(inputDirectory.resolve("FSTEP-WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"paramValue1_1,paramValue1_2\"\n" +
                        "param2=\"paramValue2_1,paramValue2_2\"\n"
        );

        JSONAssert.assertEquals("{  \"param1\": [\"paramValue1_1\", \"paramValue1_2\"],  \"param2\": [\"paramValue2_1\", \"paramValue2_2\"] }", StringUtils.readAsString(inputDirectory.resolve(JOB_INPUTS_JSON_FILENAME)), JSONCompareMode.STRICT);
    }

    @Test
    public void testPrepareEnvironment_DoesNotCreateInputFiles_WhenInputsAreEmpty() throws Exception {
        EnvironmentService environmentService = createEnvironmentService("{}");

        environmentService.prepareEnvironment("42", inputDirectory, USER_UUID);

        inOrder.verify(downloaderService, times(1)).downloadInputs(inputDirectory.resolve("inDir"), new ArrayList<>(), USER_UUID);

        assertThat(FilesUtils.walk(inputDirectory)).containsExactlyInAnyOrder(
                fileSystem.getPath("/tmp"),
                fileSystem.getPath("/tmp/inDir"),
                fileSystem.getPath("/tmp/outDir"),
                fileSystem.getPath("/tmp/persistent")
        );

        assertThat(Files.getPosixFilePermissions(inputDirectory.resolve("outDir"))).isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));

        assertThat(Files.exists(inputDirectory.resolve(JOB_INPUTS_JSON_FILENAME))).isFalse();
        assertThat(Files.exists(inputDirectory.resolve("FSTEP-WPS-INPUT.properties"))).isFalse();
        assertThat(Files.exists(inputDirectory.resolve("WPS-INPUT.properties"))).isFalse();
    }

    @Test
    public void testCreateJobInputsJson_WritesValueCorrectly_WhenCardinalityIsOne() throws Exception {

        EnvironmentService environmentService = createEnvironmentService("{\"param1\": {\"type\": \"OTHER\", \"values\": [\"onlyValue\"]}}");

        environmentService.prepareEnvironment("42", inputDirectory, USER_UUID);

        List<K8sJobParams.DownloadableParam> downloadableParams = new ArrayList<>();
        inOrder.verify(downloaderService, times(1)).downloadInputs(inputDirectory.resolve("inDir"), downloadableParams, USER_UUID);

        assertThat(FilesUtils.walk(inputDirectory)).containsExactlyInAnyOrder(
                fileSystem.getPath("/tmp"),
                fileSystem.getPath("/tmp/FSTEP-WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/WPS-INPUT.properties"),
                fileSystem.getPath("/tmp/jobInputs.json"),
                fileSystem.getPath("/tmp/inDir"),
                fileSystem.getPath("/tmp/outDir"),
                fileSystem.getPath("/tmp/persistent")
        );

        assertThat(Files.getPosixFilePermissions(inputDirectory.resolve("outDir"))).isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));

        assertThat(StringUtils.readAsString(inputDirectory.resolve("WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"onlyValue\"\n"
        );

        assertThat(StringUtils.readAsString(inputDirectory.resolve("FSTEP-WPS-INPUT.properties"))).isEqualToNormalizingNewlines(
                "param1=\"onlyValue\"\n"
        );

        JSONAssert.assertEquals("{  \"param1\": [\"onlyValue\"] }", StringUtils.readAsString(inputDirectory.resolve(JOB_INPUTS_JSON_FILENAME)), JSONCompareMode.STRICT);
    }

    @Test
    public void testCreateJobInputsJson_ThrowsIOException_WhenJsonFileCannotBeWritten() {

        Path invalidDirectory = fileSystem.getPath("/root/non_writable");

        EnvironmentService environmentService = createEnvironmentService("{\"param1\": {\"type\": \"URL\", \"values\": [\"v1\"]}}");

        assertThatThrownBy(() -> environmentService.prepareEnvironment("42", invalidDirectory, USER_UUID))
                .isInstanceOf(IOException.class);
    }

    private EnvironmentService createEnvironmentService(String inputs) {
        return new EnvironmentService(
                stageInService,
                downloaderService,
                createK8sJobParams(inputs),
                OBJECT_MAPPER
        );

    }

    private static K8sJobParams createK8sJobParams(String inputs) {
        return new K8sJobParams(inputs, OBJECT_MAPPER);
    }
}