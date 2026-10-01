package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.testutils.core.FilesUtils;
import com.cgi.eoss.platform.testutils.core.StringUtils;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.request;
import static com.cgi.eoss.platform.testutils.web.MockWebServerWrapper.response;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { InputDownloaderCoreTestConfig.class})
@TestPropertySource(
        locations = {"classpath:test-input-downloader-core.properties"},
        properties = {"inputs={\"stacParam\": {\"type\": \"STAC\", \"values\": [\"http://localhost:${platform.mockserver.port}/stacInput/00.json\"]}," +
                "\"urlParamOne\": {\"type\": \"URL\", \"values\": [\"http://localhost:${platform.mockserver.port}/urlInputOne/00.xml\", \"http://localhost:${platform.mockserver.port}/urlInputOne/01.xml\"]}," +
                "\"urlParamTwo\": {\"type\": \"URL\", \"values\": [\"http://localhost:${platform.mockserver.port}/urlInputTwo/01.xml\"]} }"} )
public abstract class EnvironmentServiceIT {

    protected static final String USER_UUID = "user_uuid";
    protected final MockWebServerWrapper webServer = new MockWebServerWrapper(new MockWebServer());

    @Autowired
    protected EnvironmentService environmentService;

    @Autowired
    protected FileSystem fs;

    @Value("${platform.mockserver.port}")
    protected int mockServerPort;
    protected Path baseDir;

    @Before
    public void setUp() throws IOException {
        webServer.start(mockServerPort);
        baseDir = Files.createDirectories(fs.getPath("/basePath"));
    }

    @After
    public void shutdown() throws IOException {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }

        FilesUtils.deleteDirContentsIfExists(baseDir);
        Files.deleteIfExists(baseDir);
    }

    @TestPropertySource(properties = {
            "platform.inputdownloader.createSubdirectories=true"
    })
    public static class EnvironmentServiceWithDownloaderServiceCreateSubdirTrueIT extends EnvironmentServiceIT {

        @Test
        public void testPrepareEnvironment_CreatesConfigFilesAndDownloadsInputs_WhenInputAreStacAndUrl() throws Exception {
            webServer.expect(
                    request(HttpMethod.GET, "/stacInput/00.json"),
                    response(HttpStatus.OK, StringUtils.readResourceAsString(Paths.get("stage-in/STAC-document.json")))
            );
            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset.bat"),
                    response(HttpStatus.OK, "fake-asset.bat")
            );
            webServer.expect(
                    request(HttpMethod.GET, "/urlInputOne/00.xml"),
                    response(HttpStatus.OK, "url-input-body-one-00.xml")
            );
            webServer.expect(
                    request(HttpMethod.GET, "/urlInputOne/01.xml"),
                    response(HttpStatus.OK, "url-input-body-one-01.xml")
            );
            webServer.expect(
                    request(HttpMethod.GET, "/urlInputTwo/01.xml"),
                    response(HttpStatus.OK, "url-input-body-two-01.xml")
            );

            environmentService.prepareEnvironment("jobId", baseDir, USER_UUID);

            assertThat(FilesUtils.walk(baseDir)).containsExactlyInAnyOrder(
                    fs.getPath("/basePath"),
                    fs.getPath("/basePath/WPS-INPUT.properties"),
                    fs.getPath("/basePath/FSTEP-WPS-INPUT.properties"),
                    fs.getPath("/basePath/inDir"),
                    fs.getPath("/basePath/inDir/urlParamOne"),
                    fs.getPath("/basePath/inDir/urlParamOne/00.xml"),
                    fs.getPath("/basePath/inDir/urlParamOne/00.xml/00.xml"),
                    fs.getPath("/basePath/inDir/urlParamOne/01.xml"),
                    fs.getPath("/basePath/inDir/urlParamOne/01.xml/01.xml"),
                    fs.getPath("/basePath/inDir/urlParamTwo"),
                    fs.getPath("/basePath/inDir/urlParamTwo/01.xml"),
                    fs.getPath("/basePath/inDir/stacParam"),
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A"),
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02"),
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat"),
                    fs.getPath("/basePath/jobInputs.json"),
                    fs.getPath("/basePath/outDir"),
                    fs.getPath("/basePath/persistent"),
                    fs.getPath("/basePath/stageIn"),
                    fs.getPath("/basePath/stageIn/stacParam"),
                    fs.getPath("/basePath/stageIn/stacParam/S2B_53HPA_20210723_0_L2A.json"),
                    fs.getPath("/basePath/stageIn/stacParam/catalog.json")
            );
            assertThat(Files.readAllLines(fs.getPath("/basePath/WPS-INPUT.properties")))
                    .containsExactlyInAnyOrder(
                            "stacParam=\"http://localhost:10801/stacInput/00.json\"",
                            "urlParamOne=\"http://localhost:10801/urlInputOne/00.xml,http://localhost:10801/urlInputOne/01.xml\"",
                            "urlParamTwo=\"http://localhost:10801/urlInputTwo/01.xml\""
                    );
            assertThat(Files.readAllLines(fs.getPath("/basePath/FSTEP-WPS-INPUT.properties")))
                    .containsExactlyInAnyOrder(
                            "stacParam=\"http://localhost:10801/stacInput/00.json\"",
                            "urlParamOne=\"http://localhost:10801/urlInputOne/00.xml,http://localhost:10801/urlInputOne/01.xml\"",
                            "urlParamTwo=\"http://localhost:10801/urlInputTwo/01.xml\""
                    );
            assertThat(StringUtils.readAsString(fs.getPath("/basePath/inDir/urlParamOne/00.xml/00.xml")))
                    .isEqualTo("url-input-body-one-00.xml");
            assertThat(StringUtils.readAsString(fs.getPath("/basePath/inDir/urlParamOne/01.xml/01.xml")))
                    .isEqualTo("url-input-body-one-01.xml");
            assertThat(StringUtils.readAsString(fs.getPath("/basePath/inDir/urlParamTwo/01.xml")))
                    .isEqualTo("url-input-body-two-01.xml");
            assertThat(StringUtils.readAsString(
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat")))
                    .isEqualTo("fake-asset.bat");
            JSONAssert.assertEquals(
                    "{\"stacParam\":[\"http://localhost:10801/stacInput/00.json\"]," +
                            "\"urlParamOne\":[\"http://localhost:10801/urlInputOne/00.xml\",\"http://localhost:10801/urlInputOne/01.xml\"]," +
                            "\"urlParamTwo\":[\"http://localhost:10801/urlInputTwo/01.xml\"]}",
                    StringUtils.readAsString(fs.getPath("/basePath/jobInputs.json")),
                    JSONCompareMode.STRICT
            );
            JSONAssert.assertEquals(
                    "{\"id\":\"S2B_53HPA_20210723_0_L2A\",\"assets\":{\"B02\":{\"href\":\"/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat\",\"proj:shape\":[10980,10980],\"proj:transform\":[10,0,600000,0,-10,6100000,0,0,1],\"roles\":[\"data\"],\"eo:bands\":[{\"name\":\"B02\",\"common_name\":\"blue\",\"center_wavelength\":0.4966,\"full_width_half_max\":0.098}],\"gsd\":10,\"type\":\"image/tiff; profile=cloud-optimized; application=geotiff\",\"title\":\"Band 2 (blue)\",\"file:size\":206117177}},\"stac_version\":\"1.0.0\",\"bbox\":[136.09905192261127,-36.22788818051635,137.33381497932513,-35.22113204961173],\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[136.11273785955868,-36.22788818051635],[136.09905192261127,-35.238096451039816],[137.30513468251897,-35.22113204961173],[137.33381497932513,-36.21029815477051],[136.11273785955868,-36.22788818051635]]]},\"links\":[{\"type\":\"application/json\",\"rel\":\"canonical\",\"href\":\"https://sentinel-cogs.s3.us-west-2.amazonaws.com/sentinel-s2-l2a-cogs/53/H/PA/2021/7/S2B_53HPA_20210723_0_L2A/S2B_53HPA_20210723_0_L2A.json\"},{\"rel\":\"parent\",\"href\":\"../catalog.json\"}],\"type\":\"Feature\",\"stac_extensions\":[\"eo\",\"proj\",\"view\"],\"properties\":{\"datetime\":\"2021-07-23T00:57:07Z\",\"platform\":\"sentinel-2b\",\"constellation\":\"sentinel-2\",\"gsd\":10}}",
                    StringUtils.readAsString(fs.getPath("/basePath/stageIn/stacParam/S2B_53HPA_20210723_0_L2A.json")),
                    JSONCompareMode.STRICT
            );

            JSONAssert.assertEquals(
                    "{\"id\":\"stacParam\",\"type\":\"Catalog\",\"description\":\"Root catalog for input stacParam\",\"links\":[{\"rel\":\"item\",\"href\":\"/basePath/stageIn/stacParam/S2B_53HPA_20210723_0_L2A.json\",\"type\":\"application/geo+json\",\"title\":\"S2B_53HPA_20210723_0_L2A\"}],\"stac_version\":\"1.0.0\"}",
                    StringUtils.readAsString(fs.getPath("/basePath/stageIn/stacParam/catalog.json")),
                    JSONCompareMode.STRICT
            );assertThat(Files.getPosixFilePermissions(baseDir.resolve("outDir")))
                    .isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));
        }
    }

    @TestPropertySource(properties = {
            "platform.inputdownloader.createSubdirectories=false"
    })
    public static class EnvironmentServiceWithDownloaderServiceCreateSubdirFalseIT extends EnvironmentServiceIT {

        @Test
        public void testPrepareEnvironment_CreatesConfigFilesAndDownloadsInputs_WhenInputAreStacAndUrl() throws Exception {
            webServer.expect(
                    request(HttpMethod.GET, "/stacInput/00.json"),
                    response(HttpStatus.OK, StringUtils.readResourceAsString(Paths.get("stage-in/STAC-document.json")))
            );
            webServer.expect(
                    request(HttpMethod.GET, "/download/fake-asset.bat"),
                    response(HttpStatus.OK, "fake-asset.bat")
            );
            webServer.expect(
                    request(HttpMethod.GET, "/urlInputOne/00.xml"),
                    response(HttpStatus.OK, "url-input-body-one-00.xml")
            );
            webServer.expect(
                    request(HttpMethod.GET, "/urlInputOne/01.xml"),
                    response(HttpStatus.OK, "url-input-body-one-01.xml")
            );
            webServer.expect(
                    request(HttpMethod.GET, "/urlInputTwo/01.xml"),
                    response(HttpStatus.OK, "url-input-body-two-01.xml")
            );

            environmentService.prepareEnvironment("jobId", baseDir, USER_UUID);

            assertThat(FilesUtils.walk(baseDir)).containsExactlyInAnyOrder(
                    fs.getPath("/basePath"),
                    fs.getPath("/basePath/WPS-INPUT.properties"),
                    fs.getPath("/basePath/FSTEP-WPS-INPUT.properties"),
                    fs.getPath("/basePath/inDir"),
                    fs.getPath("/basePath/inDir/urlParamOne"),
                    fs.getPath("/basePath/inDir/urlParamOne/00.xml"),
                    fs.getPath("/basePath/inDir/urlParamOne/01.xml"),
                    fs.getPath("/basePath/inDir/urlParamTwo"),
                    fs.getPath("/basePath/inDir/urlParamTwo/01.xml"),
                    fs.getPath("/basePath/inDir/stacParam"),
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A"),
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02"),
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat"),
                    fs.getPath("/basePath/jobInputs.json"),
                    fs.getPath("/basePath/outDir"),
                    fs.getPath("/basePath/persistent"),
                    fs.getPath("/basePath/stageIn"),
                    fs.getPath("/basePath/stageIn/stacParam"),
                    fs.getPath("/basePath/stageIn/stacParam/S2B_53HPA_20210723_0_L2A.json"),
                    fs.getPath("/basePath/stageIn/stacParam/catalog.json")
            );
            assertThat(Files.readAllLines(fs.getPath("/basePath/WPS-INPUT.properties")))
                    .containsExactlyInAnyOrder(
                            "stacParam=\"http://localhost:10801/stacInput/00.json\"",
                            "urlParamOne=\"http://localhost:10801/urlInputOne/00.xml,http://localhost:10801/urlInputOne/01.xml\"",
                            "urlParamTwo=\"http://localhost:10801/urlInputTwo/01.xml\""
                    );
            assertThat(Files.readAllLines(fs.getPath("/basePath/FSTEP-WPS-INPUT.properties")))
                    .containsExactlyInAnyOrder(
                            "stacParam=\"http://localhost:10801/stacInput/00.json\"",
                            "urlParamOne=\"http://localhost:10801/urlInputOne/00.xml,http://localhost:10801/urlInputOne/01.xml\"",
                            "urlParamTwo=\"http://localhost:10801/urlInputTwo/01.xml\""
                    );
            assertThat(StringUtils.readAsString(fs.getPath("/basePath/inDir/urlParamOne/00.xml")))
                    .isEqualTo("url-input-body-one-00.xml");
            assertThat(StringUtils.readAsString(fs.getPath("/basePath/inDir/urlParamOne/01.xml")))
                    .isEqualTo("url-input-body-one-01.xml");
            assertThat(StringUtils.readAsString(fs.getPath("/basePath/inDir/urlParamTwo/01.xml")))
                    .isEqualTo("url-input-body-two-01.xml");
            assertThat(StringUtils.readAsString(
                    fs.getPath("/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat")))
                    .isEqualTo("fake-asset.bat");
            JSONAssert.assertEquals(
                    "{\"stacParam\":[\"http://localhost:10801/stacInput/00.json\"]," +
                            "\"urlParamOne\":[\"http://localhost:10801/urlInputOne/00.xml\",\"http://localhost:10801/urlInputOne/01.xml\"]," +
                            "\"urlParamTwo\":[\"http://localhost:10801/urlInputTwo/01.xml\"]}",
                    StringUtils.readAsString(fs.getPath("/basePath/jobInputs.json")),
                    JSONCompareMode.STRICT
            );
            JSONAssert.assertEquals(
                    "{\"id\":\"S2B_53HPA_20210723_0_L2A\",\"assets\":{\"B02\":{\"href\":\"/basePath/inDir/stacParam/S2B_53HPA_20210723_0_L2A/B02/fake-asset.bat\",\"proj:shape\":[10980,10980],\"proj:transform\":[10,0,600000,0,-10,6100000,0,0,1],\"roles\":[\"data\"],\"eo:bands\":[{\"name\":\"B02\",\"common_name\":\"blue\",\"center_wavelength\":0.4966,\"full_width_half_max\":0.098}],\"gsd\":10,\"type\":\"image/tiff; profile=cloud-optimized; application=geotiff\",\"title\":\"Band 2 (blue)\",\"file:size\":206117177}},\"stac_version\":\"1.0.0\",\"bbox\":[136.09905192261127,-36.22788818051635,137.33381497932513,-35.22113204961173],\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[136.11273785955868,-36.22788818051635],[136.09905192261127,-35.238096451039816],[137.30513468251897,-35.22113204961173],[137.33381497932513,-36.21029815477051],[136.11273785955868,-36.22788818051635]]]},\"links\":[{\"type\":\"application/json\",\"rel\":\"canonical\",\"href\":\"https://sentinel-cogs.s3.us-west-2.amazonaws.com/sentinel-s2-l2a-cogs/53/H/PA/2021/7/S2B_53HPA_20210723_0_L2A/S2B_53HPA_20210723_0_L2A.json\"},{\"rel\":\"parent\",\"href\":\"../catalog.json\"}],\"type\":\"Feature\",\"stac_extensions\":[\"eo\",\"proj\",\"view\"],\"properties\":{\"datetime\":\"2021-07-23T00:57:07Z\",\"platform\":\"sentinel-2b\",\"constellation\":\"sentinel-2\",\"gsd\":10}}",
                    StringUtils.readAsString(fs.getPath("/basePath/stageIn/stacParam/S2B_53HPA_20210723_0_L2A.json")),
                    JSONCompareMode.STRICT
            );

            JSONAssert.assertEquals(
                    "{\"id\":\"stacParam\",\"type\":\"Catalog\",\"description\":\"Root catalog for input stacParam\",\"links\":[{\"rel\":\"item\",\"href\":\"/basePath/stageIn/stacParam/S2B_53HPA_20210723_0_L2A.json\",\"type\":\"application/geo+json\",\"title\":\"S2B_53HPA_20210723_0_L2A\"}],\"stac_version\":\"1.0.0\"}",
                    StringUtils.readAsString(fs.getPath("/basePath/stageIn/stacParam/catalog.json")),
                    JSONCompareMode.STRICT
            );assertThat(Files.getPosixFilePermissions(baseDir.resolve("outDir")))
                    .isEqualTo(PosixFilePermissions.fromString("rwxrwxr-x"));
        }
    }

}
