package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDockerBuildInfo;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.exceptions.CwlValidationException;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserMountDataService;
import com.cgi.eoss.platform.testutils.web.MockWebServerWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.cgi.eoss.platform.testutils.core.StringUtils.readAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class CwlServiceTest {

    private static final Path BASE_TEST_PATH = Paths.get("src", "test", "resources", "cwl");

    private static final int MOCKWEBSERVER_PORT = 9090;

    private static final String FORMAT = "format";

    private static final String CATALOGUE = "CATALOGUE";

    private static final String OTHER = "OTHER";

    private static final String TYPE = "type";

    private static final String STAC = "STAC";

    private static final String PREVENT_URL_DOWNLOAD = "preventUrlDownload";

    private CwlService cwlService;

    private MockWebServerWrapper webServer;
    
    @Mock
    private UserMountDataService userMountDataService;

    private InOrder inOrder;
    

    @Before
    public void init() throws Exception {

        MockitoAnnotations.initMocks(this);
        inOrder=inOrder(userMountDataService);

        webServer = new MockWebServerWrapper(new MockWebServer());
        webServer.start(MOCKWEBSERVER_PORT);
        cwlService = new CwlService(new OkHttpClient().newBuilder().build(), new CwlServiceProperties(), userMountDataService);
    }

    @After
    public void shutdown() {
        try {
            webServer.verifyExpectedRequests();
        } finally {
            webServer.shutdown();
        }

        inOrder.verifyNoMoreInteractions();
    }


    @Test
    public void testPopulateFromCwl_ThrowsIllegalArgumentException_WhenUserMountNameDoesNotExistInDb() {

        String cwlAsString = readAsString(BASE_TEST_PATH.resolve("app-package.cwl"));
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, cwlAsString));
        }

        when(userMountDataService.getByName("userMount")).thenReturn(Optional.empty());
        URI cwlUrl = URI.create("http://localhost:"+MOCKWEBSERVER_PORT+"/cwl/app-package.cwl");
        assertThatThrownBy(() -> cwlService.populateFromCwl(cwlUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UserMount name: userMount not found");

        inOrder.verify(userMountDataService, times(1)).getByName("userMount");
    }


    @Test
    public void testPopulateFromCwl_ReturnsPlatformServiceWithProvidedCwlAttributesAndVersion_WhenVersionIsSet() {
        String cwlAsString = readAsString(BASE_TEST_PATH.resolve("app-package.cwl"));
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, cwlAsString));
        }

        UserMount userMount = new UserMount();
        userMount.setId(1L);
        userMount.setName("userMount");
        userMount.setMountPath("/base/mount/1");
        when(userMountDataService.getByName("userMount")).thenReturn(Optional.of(userMount));

        URI cwlUrl = URI.create("http://localhost:"+MOCKWEBSERVER_PORT+"/cwl/app-package.cwl");
        PlatformService platformService = cwlService.populateFromCwl(cwlUrl);

        inOrder.verify(userMountDataService, times(1)).getByName("userMount");

        Map<Long, String> expectedUserMount = new HashMap<>();
        expectedUserMount.put(1L, "home/first");
        assertThat(platformService.getAdditionalMounts()).containsExactlyEntriesOf(
                expectedUserMount
        );

        // Verify PlatformService attributes
        assertThat(platformService.getName()).isEqualTo("s2-cropper");
        assertThat(platformService.getDescription()).isEqualTo("This application crops a Sentinel-2 band");
        assertThat(platformService.getDockerTag()).isEqualTo("ogc-crop:0.1");
        assertThat(platformService.getType()).isEqualTo(PlatformService.Type.PROCESSOR);
        PlatformServiceDockerBuildInfo expectedDockerBuildInfo = new PlatformServiceDockerBuildInfo();
        expectedDockerBuildInfo.setDockerBuildStatus(PlatformServiceDockerBuildInfo.Status.COMPLETED);
        assertThat(platformService.getDockerBuildInfo()).isEqualTo(expectedDockerBuildInfo);
        assertThat(platformService.getCwl().getUrl()).isEqualTo(cwlUrl);
        assertThat(platformService.getCwl().getDocument()).isEqualTo(cwlAsString);

        // Verify ServiceDescriptor attributes
        PlatformServiceDescriptor actualServiceDescriptor = platformService.getServiceDescriptor();
        assertThat(actualServiceDescriptor.getId()).isEqualTo("s2-cropper");
        assertThat(actualServiceDescriptor.getTitle()).isEqualTo("Sentinel-2 band crop");
        assertThat(actualServiceDescriptor.getDescription()).isEqualTo("This application crops a Sentinel-2 band");
        assertThat(actualServiceDescriptor.getDockerCommand()).isEqualTo("crop");
        assertThat(actualServiceDescriptor.getDockerArguments()).isEqualTo(ImmutableList.of("arg1", "arg2"));
        assertThat(actualServiceDescriptor.getVersion()).isEqualTo("1.0.0");
        assertThat(actualServiceDescriptor.getEnvironmentVariables()).isEqualTo(
                ImmutableMap.of("PATH", "/opt/sbin:/bin")
        );
        assertThat(actualServiceDescriptor.getDataInputs()).containsExactlyInAnyOrder(
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputDirectory").title("Sentinel-2 inputs")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(1).build())
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
                        .id("inputEnum").title("enum")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(4).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).description("enum type")
                        .defaultAttrs(ImmutableMap.of("dataType", "string", "allowedValues", "bam,sam"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputArray").title("inputArray")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(5).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "true"))
                        .minOccurs(1).maxOccurs(100)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputInt").title("inputInt")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(6).build())
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .defaultAttrs(ImmutableMap.of("dataType", "integer")).data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputLong").title("inputLong")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(7).build())
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .defaultAttrs(ImmutableMap.of("dataType", "integer")).data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL)
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputFile").title("inputFile")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(8).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER, PREVENT_URL_DOWNLOAD, "false"))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("inputBool").title("inputBool")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(9).build())
                        .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "boolean"))
                        .platformMetadata(ImmutableMap.of(FORMAT, OTHER))
                        .minOccurs(1).maxOccurs(1)
                        .build(),
                PlatformServiceDescriptor.Parameter.builder()
                        .id("OptArray").title("OptArray")
                        .inputBinding(PlatformServiceDescriptor.InputBinding.builder().position(10).build())
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
                .id("cropped_tif").title("Cropped band").description("Cropped Sentinel-2 band")
                .data(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL).defaultAttrs(ImmutableMap.of("dataType", "string"))
                .platformMetadata(ImmutableMap.of(FORMAT, CATALOGUE, TYPE, STAC, PREVENT_URL_DOWNLOAD, "false"))
                .minOccurs(1).maxOccurs(1)
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
    }

    @Test
    public void testPopulateFromCwl_ThrowsIllegalArgumentException_WhenCwlUrlIsNotValid() {
        URI cwlUrl = URI.create("malformedURL");
        assertThatThrownBy(() -> cwlService.populateFromCwl(cwlUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URI is not absolute");
    }

    @Test
    public void testPopulateFromCwl_ThrowsCwlValidationException_WhenCwlIsNotValid() {
        String cwlAsString = readAsString(BASE_TEST_PATH.resolve("app-package-without-workflow.cwl"));
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package-without-workflow.cwl"),
                    MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, cwlAsString));
        }

        URI cwlUrl = URI.create("http://localhost:"+MOCKWEBSERVER_PORT+"/cwl/app-package-without-workflow.cwl");

        assertThatThrownBy(() -> cwlService.populateFromCwl(cwlUrl))
                .isInstanceOf(CwlValidationException.class)
                .hasMessageContaining("CWL Missing Workflow has an unexpected number of Workflows: it must contain exactly one.");
    }

    @Test
    public void testPopulateFromCwl_ThrowsIllegalStateException_WhenDownloadResponseStatusIsNotValid() {
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(HttpStatus.NOT_FOUND, "error"));
        }
        URI cwlUrl = URI.create("http://localhost:"+MOCKWEBSERVER_PORT+"/cwl/app-package.cwl");

        assertThatThrownBy(() -> cwlService.populateFromCwl(cwlUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Received invalid HTTP status code 404 from URL " + cwlUrl);
    }

    @Test
    public void testPopulateFromCwl_ThrowsIllegalStateException_WhenDownloadResponseBodyExceedsMaximumSize() {
        int maximumSizeDefault = (1024*100)+1;

        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(HttpStatus.NOT_MODIFIED, new String(new byte[maximumSizeDefault+1])));
        }

        assertThatThrownBy(() -> cwlService.populateFromCwl(
                URI.create("http://localhost:" + MOCKWEBSERVER_PORT + "/cwl/app-package.cwl")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Response exceeds maximum expected size " + maximumSizeDefault);
    }

    @Test
    public void testPopulateFromCwl_ThrowsIllegalStateException_WhenDownloadResponseBodyIsEmpty() {
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(null, SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        }

        assertThatThrownBy(() -> cwlService.populateFromCwl(
                URI.create("http://localhost:" + MOCKWEBSERVER_PORT + "/cwl/app-package.cwl")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Received an empty response body.");
    }

    @Test
    public void testPopulateFromCwl_ThrowsUncheckedIOException_WhenServerDisconnectsDuringResponseBodyStream() {
        {
            webServer.expect(MockWebServerWrapper.request(HttpMethod.GET, "/cwl/app-package.cwl"),
                    MockWebServerWrapper.response(null, SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY, "non-empty body"));
        }

        assertThatThrownBy(() -> cwlService.populateFromCwl(
                URI.create("http://localhost:" + MOCKWEBSERVER_PORT + "/cwl/app-package.cwl")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("unexpected end of stream");
    }
}
