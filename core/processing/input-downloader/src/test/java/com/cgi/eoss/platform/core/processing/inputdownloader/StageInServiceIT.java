package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.ServiceIoException;
import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static com.cgi.eoss.platform.testutils.core.StringUtils.readResourceAsString;
import static com.cgi.eoss.platform.testutils.core.constants.CommonPaths.BASE_TEST_PATH;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { InputDownloaderCoreTestConfig.class })
@TestPropertySource(locations = { "classpath:test-input-downloader-core.properties" })
public class StageInServiceIT {

    private static final CustomComparator CATALOG_COMPARATOR = new CustomComparator(JSONCompareMode.STRICT,
            regexMatcher("id"),
            regexMatcher("description"),
            regexMatcher("links[*].href"));

    private static final CustomComparator DOCUMENT_COMPARATOR = new CustomComparator(JSONCompareMode.STRICT,
            regexMatcher("assets.B02.href"));

    @Autowired
    private StageInService stageInService;

    @Autowired
    private FileSystem fileSystem;

    @Value("${platform.mockserver.port}")
    private int mockServerPort;

    private String mockServerUrl;

    private MockWebServerWrapper webServer;

    private Path inputDir;

    private Path stageInDir;

    @Before
    public void setUp() throws Exception {
        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(mockServerPort);

        mockServerUrl = "http://localhost:" + mockServerPort;

        deleteFilesSystem();
    }

    @After
    public void shutdown() throws Exception {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }
        deleteFilesSystem();
    }

    @Test
    public void testStageInService_CreatesFolderStructureContainingAssetsAndStageInFolderContainingCatalogAndItemStacFiles() throws Exception {

        {
            webServer.expect(
                    request(HttpMethod.GET, "/eopaas-stac-items/jobId/stacInputTwo/01.json"),
                    response(HttpStatus.OK, readResourceAsString(Paths.get("stage-in/STAC-document-second-input.json"))));

            webServer.expect(
                    request(HttpMethod.GET, "/eopaas-stac-items/jobId/stacInputOne/00.json"),
                    response(HttpStatus.OK, readResourceAsString(Paths.get("stage-in/STAC-document.json"))));

            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset-2.bat"),
                    response(HttpStatus.OK, "fake-asset-2.bat"));

            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset-3.bat"),
                    response(HttpStatus.OK, "fake-asset-3.bat"));

            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset.bat"),
                    response(HttpStatus.OK, "fake-asset.bat"));
        }

        Multimap<String, String> inputs = ArrayListMultimap.create();

        String inputId = "stacInputOne";
        inputs.put(inputId, mockServerUrl + "/eopaas-stac-items/jobId/stacInputOne/00.json");

        String secondInputId = "stacInputTwo";
        inputs.put(secondInputId, mockServerUrl + "/eopaas-stac-items/jobId/stacInputTwo/01.json");

        prepareEnvironment(inputId);
        prepareEnvironment(secondInputId);

        Path stageInDir = fileSystem.getPath("/data").resolve("stageIn");
        Files.createDirectories(stageInDir);

        stageInService.stageInInputs(stageInDir, inputDir, inputs, "jobOwner");

        assertThat(FilesUtils.walk(fileSystem.getPath("/data"))).containsExactlyInAnyOrder(
                fileSystem.getPath("/data"),
                fileSystem.getPath("/data/tempDir"),
                fileSystem.getPath("/data/tempDir/stacInputOne"),
                fileSystem.getPath("/data/tempDir/stacInputOne/00.json"),
                fileSystem.getPath("/data/tempDir/stacInputTwo"),
                fileSystem.getPath("/data/tempDir/stacInputTwo/01.json"),
                fileSystem.getPath("/data/inputDir"),
                fileSystem.getPath("/data/inputDir/stacInputOne"),
                fileSystem.getPath("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A"),
                fileSystem.getPath("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A/B02"),
                fileSystem.getPath("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat"),
                fileSystem.getPath("/data/inputDir/stacInputTwo"),
                fileSystem.getPath("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_2"),
                fileSystem.getPath("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_2/B02"),
                fileSystem.getPath("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_2/B02/fake-asset-2.bat"),
                fileSystem.getPath("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_3"),
                fileSystem.getPath("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_3/B02"),
                fileSystem.getPath("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_3/B02/fake-asset-3.bat"),
                fileSystem.getPath("/data/stageIn"),
                fileSystem.getPath("/data/stageIn/stacInputOne"),
                fileSystem.getPath("/data/stageIn/stacInputOne/S2B_53HPA_20210723_0_L2A.json"),
                fileSystem.getPath("/data/stageIn/stacInputOne/catalog.json"),
                fileSystem.getPath("/data/stageIn/stacInputTwo"),
                fileSystem.getPath("/data/stageIn/stacInputTwo/S2B_53HPA_20210723_0_L2A_2.json"),
                fileSystem.getPath("/data/stageIn/stacInputTwo/S2B_53HPA_20210723_0_L2A_3.json"),
                fileSystem.getPath("/data/stageIn/stacInputTwo/catalog.json")
        );

        // checks on the items json's
        String actualDocumentContentFirstInput = readAsString(fileSystem.getPath("/data/stageIn/stacInputOne/S2B_53HPA_20210723_0_L2A.json"));
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/STAC-item-with-local-path-references.json")),
                actualDocumentContentFirstInput, DOCUMENT_COMPARATOR);

        String actualDocumentContentSecondInputItem1 = readAsString(fileSystem.getPath("/data/stageIn/stacInputTwo/S2B_53HPA_20210723_0_L2A_2.json"));
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/STAC-item-second-input-with-local-path-references-firstItem.json")),
                actualDocumentContentSecondInputItem1, DOCUMENT_COMPARATOR);

        String actualDocumentContentSecondInputItem2 = readAsString(fileSystem.getPath("/data/stageIn/stacInputTwo/S2B_53HPA_20210723_0_L2A_3.json"));
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/STAC-item-second-input-with-local-path-references-secondItem.json")),
                actualDocumentContentSecondInputItem2, DOCUMENT_COMPARATOR);

        DocumentContext actualJsonDocumentContent = JsonPath.parse(actualDocumentContentFirstInput);
        assertThat(actualJsonDocumentContent.<String>read("assets.B02.href"))
                .isEqualTo("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat");

        DocumentContext actualJsonDocumentContentSecondInputItem1 = JsonPath.parse(actualDocumentContentSecondInputItem1);
        assertThat(actualJsonDocumentContentSecondInputItem1.<String>read("assets.B02.href"))
                .isEqualTo("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_2/B02/fake-asset-2.bat");

        DocumentContext actualJsonDocumentContentSecondInputItem2 = JsonPath.parse(actualDocumentContentSecondInputItem2);
        assertThat(actualJsonDocumentContentSecondInputItem2.<String>read("assets.B02.href"))
                .isEqualTo("/data/inputDir/stacInputTwo/S2B_53HPA_20210723_0_L2A_3/B02/fake-asset-3.bat");

        // checks on the catalogs
        String actualCatalogContent = readAsString(fileSystem.getPath("/data/stageIn/stacInputOne/catalog.json")) ;
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/expected-catalog-single-item.json")),
                actualCatalogContent, CATALOG_COMPARATOR);

        DocumentContext actualJsonCatalogContent = JsonPath.parse(actualCatalogContent);
        assertThat(actualJsonCatalogContent.<String>read("id"))
                .isEqualTo("stacInputOne");
        assertThat(actualJsonCatalogContent.<String>read("description"))
                .isEqualTo("Root catalog for input stacInputOne");
        assertThat(actualJsonCatalogContent.<String>read("links[0].href"))
                .isEqualTo("/data/stageIn/stacInputOne/S2B_53HPA_20210723_0_L2A.json");

        String actualCatalogContentSecondInput = readAsString(fileSystem.getPath("/data/stageIn/stacInputTwo/catalog.json")) ;
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/expected-catalog-multi-items.json")),
                actualCatalogContentSecondInput, CATALOG_COMPARATOR);

        // when there are multiple items, in the catalog they appear in reverse order due to how the item json file reference map is filled
        DocumentContext actualJsonCatalogContentSecondInput = JsonPath.parse(actualCatalogContentSecondInput);
        assertThat(actualJsonCatalogContentSecondInput.<String>read("id"))
                .isEqualTo("stacInputTwo");
        assertThat(actualJsonCatalogContentSecondInput.<String>read("description"))
                .isEqualTo("Root catalog for input stacInputTwo");
        assertThat(actualJsonCatalogContentSecondInput.<String>read("links[0].href"))
                .isEqualTo("/data/stageIn/stacInputTwo/S2B_53HPA_20210723_0_L2A_3.json");
        assertThat(actualJsonCatalogContentSecondInput.<String>read("links[1].href"))
                .isEqualTo("/data/stageIn/stacInputTwo/S2B_53HPA_20210723_0_L2A_2.json");
    }

    @Test
    public void testStageInService_CreatesFolderStructureContainingAssetsAndStageInFolderContainingCatalogAndItemStacFilesWithoutUnzippingAsset_WhenAssetIsZipFile() throws Exception {

        {
            webServer.expect(
                    request(HttpMethod.GET, "/eopaas-stac-items/jobId/stacInputOne/00.json"),
                    response(HttpStatus.OK, readResourceAsString(Paths.get("stage-in/STAC-document-with-zip-asset.json"))));

            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset.zip"),
                    response(HttpStatus.OK, Files.readAllBytes(BASE_TEST_PATH.resolve("files.zip"))));
        }

        Multimap<String, String> inputs = ArrayListMultimap.create();

        String inputId = "stacInputOne";
        inputs.put(inputId, mockServerUrl + "/eopaas-stac-items/jobId/stacInputOne/00.json");

        prepareEnvironment(inputId);

        Path stageInDir = fileSystem.getPath("/data").resolve("stageIn");
        Files.createDirectories(stageInDir);

        stageInService.stageInInputs(stageInDir, inputDir, inputs, "jobOwner");

        assertThat(FilesUtils.walk(fileSystem.getPath("/data"))).containsExactlyInAnyOrder(
                fileSystem.getPath("/data"),
                fileSystem.getPath("/data/tempDir"),
                fileSystem.getPath("/data/tempDir/stacInputOne"),
                fileSystem.getPath("/data/tempDir/stacInputOne/00.json"),
                fileSystem.getPath("/data/inputDir"),
                fileSystem.getPath("/data/inputDir/stacInputOne"),
                fileSystem.getPath("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A"),
                fileSystem.getPath("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A/B02"),
                fileSystem.getPath("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A/B02/fake-asset.zip"),
                fileSystem.getPath("/data/stageIn"),
                fileSystem.getPath("/data/stageIn/stacInputOne"),
                fileSystem.getPath("/data/stageIn/stacInputOne/S2B_53HPA_20210723_0_L2A.json"),
                fileSystem.getPath("/data/stageIn/stacInputOne/catalog.json")
        );

        // checks on the items json's
        String actualDocumentContentFirstInput = readAsString(fileSystem.getPath("/data/stageIn/stacInputOne/S2B_53HPA_20210723_0_L2A.json"));
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/STAC-item-with-local-path-references.json")),
                actualDocumentContentFirstInput, DOCUMENT_COMPARATOR);

        DocumentContext actualJsonDocumentContent = JsonPath.parse(actualDocumentContentFirstInput);
        assertThat(actualJsonDocumentContent.<String>read("assets.B02.href"))
                .isEqualTo("/data/inputDir/stacInputOne/S2B_53HPA_20210723_0_L2A/B02/fake-asset.zip");

        // checks on the catalogs
        String actualCatalogContent = readAsString(fileSystem.getPath("/data/stageIn/stacInputOne/catalog.json")) ;
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/expected-catalog-single-item.json")),
                actualCatalogContent, CATALOG_COMPARATOR);

        DocumentContext actualJsonCatalogContent = JsonPath.parse(actualCatalogContent);
        assertThat(actualJsonCatalogContent.<String>read("id"))
                .isEqualTo("stacInputOne");
        assertThat(actualJsonCatalogContent.<String>read("description"))
                .isEqualTo("Root catalog for input stacInputOne");
        assertThat(actualJsonCatalogContent.<String>read("links[0].href"))
                .isEqualTo("/data/stageIn/stacInputOne/S2B_53HPA_20210723_0_L2A.json");
    }

    @Test
    public void testStageInService_CreatesFolderStructureContainingAssetsAndStageInFolderContainingCatalogAndItemStacFiles_WhenStacInputContainsFragment() throws Exception {
        {
            webServer.expect(
                    request(HttpMethod.GET, "/eopaas-stac-items/jobId/stacInput/00.json"),
                    response(HttpStatus.OK, readResourceAsString(Paths.get("stage-in/STAC-document-multiple-features.json"))));

            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset.bat"),
                    response(HttpStatus.OK, "fake-asset.bat"));
        }

        Multimap<String, String> inputs = ArrayListMultimap.create();

        String inputId = "stacInput";
        String inputValueWithFragment = mockServerUrl + "/eopaas-stac-items/jobId/stacInput/00.json#S2B_53HPA_20210723_0_L2A";
        inputs.put(inputId, inputValueWithFragment);

        prepareEnvironment(inputId);

        stageInService.stageInInputs(stageInDir, inputDir, inputs, "jobOwner");

        assertThat(FilesUtils.walk(fileSystem.getPath("/data"))).contains(
                fileSystem.getPath("/data"),
                fileSystem.getPath("/data/tempDir"),
                fileSystem.getPath("/data/tempDir/stacInput"),
                fileSystem.getPath("/data/tempDir/stacInput/00.json"),
                fileSystem.getPath("/data/inputDir"),
                fileSystem.getPath("/data/inputDir/stacInput"),
                fileSystem.getPath("/data/inputDir/stacInput/S2B_53HPA_20210723_0_L2A"),
                fileSystem.getPath("/data/inputDir/stacInput/S2B_53HPA_20210723_0_L2A/B02"),
                fileSystem.getPath("/data/inputDir/stacInput/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat"),
                fileSystem.getPath("/data/stageIn"),
                fileSystem.getPath("/data/stageIn/stacInput"),
                fileSystem.getPath("/data/stageIn/stacInput/S2B_53HPA_20210723_0_L2A.json"),
                fileSystem.getPath("/data/stageIn/stacInput/catalog.json")
        );

        assertThat(FilesUtils.walk(fileSystem.getPath("/data"))).doesNotContain(
                fileSystem.getPath("/data/stageIn/stacInput/FEATURE_ID_TO_BE_IGNORED.json")
        );

        String actualDocumentContent = readAsString(fileSystem.getPath("/data/stageIn/stacInput/S2B_53HPA_20210723_0_L2A.json"));
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/STAC-item-with-local-path-references.json")),
                actualDocumentContent, DOCUMENT_COMPARATOR);

        DocumentContext actualJsonDocumentContent = JsonPath.parse(actualDocumentContent);
        assertThat(actualJsonDocumentContent.<String>read("assets.B02.href"))
                .isEqualTo("/data/inputDir/stacInput/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat");

        String actualCatalogContent = readAsString(fileSystem.getPath("/data/stageIn/stacInput/catalog.json")) ;
        JSONAssert.assertEquals(readResourceAsString(Paths.get("stage-in/expected-catalog-single-item.json")),
                actualCatalogContent, CATALOG_COMPARATOR);

        DocumentContext actualJsonCatalogContent = JsonPath.parse(actualCatalogContent);
        assertThat(actualJsonCatalogContent.<String>read("id"))
                .isEqualTo("stacInput");
        assertThat(actualJsonCatalogContent.<String>read("description"))
                .isEqualTo("Root catalog for input stacInput");
        assertThat(actualJsonCatalogContent.<String>read("links[0].href"))
                .isEqualTo("/data/stageIn/stacInput/S2B_53HPA_20210723_0_L2A.json");
    }

    @Test
    public void testStageInService_ThrowsServiceIoExceptionWhenDownloadWasNotCompleted() throws Exception {
        webServer.expect(
                request(HttpMethod.GET, "/download/00.json"),
                response(HttpStatus.NOT_FOUND));


        Multimap<String, String> inputs = ArrayListMultimap.create();

        String inputId = "testFile";
        inputs.put(inputId, mockServerUrl + "/download/00.json");

        prepareEnvironment(inputId);

        try {
            stageInService.stageInInputs(stageInDir, inputDir, inputs, "jobOwner");
            fail();
        } catch (ServiceIoException e) {
            assertThat(e.getMessage()).isEqualTo("No downloader was able to process the URI: " + mockServerUrl + "/download/00.json");
        }
        // Check that only the preliminary structure of the filesystem has been created
        assertThat(FilesUtils.walk(fileSystem.getPath("/data"))).containsExactlyInAnyOrder(
                fileSystem.getPath("/data"),
                fileSystem.getPath("/data/inputDir"),
                fileSystem.getPath("/data/inputDir/testFile"),
                fileSystem.getPath("/data/stageIn"),
                fileSystem.getPath("/data/tempDir"));
    }

    private void prepareEnvironment(String inputId) throws Exception {

        inputDir = fileSystem.getPath("/data").resolve("inputDir");
        Files.createDirectories(inputDir.resolve(inputId));

        stageInDir = fileSystem.getPath("/data").resolve("stageIn");
        Files.createDirectories(stageInDir);
    }


    private void deleteFilesSystem() throws Exception {
        FilesUtils.deleteDirContentsIfExists(fileSystem.getPath("/data"));
        Files.deleteIfExists(fileSystem.getPath("/data"));
    }

    private static Customization regexMatcher(String jsonPath) {
        return new Customization(jsonPath, new RegularExpressionValueMatcher<>());
    }

}