package com.cgi.eoss.platform.core.processing.io.download.s3;

import com.cgi.eoss.platform.core.processing.io.IoCoreConfig;
import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3DownloaderCoreConfiguration;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {IoCoreConfig.class})
@TestPropertySource(
        locations = {
                "classpath:test-core-io.properties"},
        properties = {
                "platform.core.io.downloader.s3.enabled=true",
        })
public class S3CoreDownloaderIT {

    @Autowired
    private S3CoreDownloader s3CoreDownloader;

    @Autowired
    private S3DownloaderCoreConfiguration s3DownloaderCoreConfiguration;

    @Value("${platform.io.downloader.port}")
    private Integer port;

    private static Integer s3Port;

    private static String s3AccessKey;

    private static String s3Region;

    private static final String USER_UUID = "undefined";

    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    private MockWebServer webServer;

    private Path targetDir;

    @Before
    public void init() throws Exception {
        targetDir = Files.createTempDirectory(Files.createDirectories(fileSystem.getPath("targetDir").toAbsolutePath()),
                "S3DownloaderTest");
        assertIsEmpty(targetDir);
        webServer = new MockWebServer();
        webServer.start(port);

        s3Port = webServer.getPort();
        s3AccessKey = s3DownloaderCoreConfiguration.getAccounts().get(0).getAccessKey();
        s3Region = s3DownloaderCoreConfiguration.getAccounts().get(0).getLocations().get(0).getRegion();
    }

    @After
    public void shutdown() throws Exception {
        webServer.shutdown();
    }

    @Test
    public void testDownload_DownloadsProductInTargetDirectory_WhenURIProtocolIsS3() throws Exception {
        String productDir = "TropForest_";
        String productName = "SM_KO2_OTPF_KO2_MSC_2F_20090811T145416_20090811T145416_016218_W068_S012.ZIP";
        String bucketName = "bucket-01";

        webServer.enqueue(new MockResponse().setBody("fileContent"));

        Path actualDownloadedFile = s3CoreDownloader.download(new DownloadRequest(new URI("s3://" + bucketName + "/" + productDir + "/" + productName), targetDir, null, USER_UUID));
        Path expectedDownloadedFile = targetDir.resolve(productDir + "/" + productName);

        assertThat(actualDownloadedFile).isEqualTo(expectedDownloadedFile);
        assertThat(walk(targetDir)).containsExactlyInAnyOrder(targetDir, targetDir.resolve(productDir),
                actualDownloadedFile);
        assertThat(new String(Files.readAllBytes(actualDownloadedFile))).isEqualTo("fileContent");

        assertThat(webServer.getRequestCount()).isEqualTo(1);
        RecordedRequest request = webServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getBodySize()).isEqualTo(0);
        verifyAuthorizationAndHostHeaders(request);
    }

    private static void verifyAuthorizationAndHostHeaders(RecordedRequest recordedRequest) {
        assertThat(recordedRequest.getHeader("Authorization")).startsWith("AWS4-HMAC-SHA256 Credential="
                + s3AccessKey + "/" + today() + "/" + s3Region + "/s3/aws4_request, " +
                "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;amz-sdk-retry;content-type;host;user-agent;x-amz-content-sha256;x-amz-date");
        assertThat(recordedRequest.getHeader("Host")).isEqualTo(InetAddress.getLoopbackAddress().getHostName() + ":" + s3Port);
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"));
    }

    private void assertIsEmpty(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            assertThat(stream.findAny().isPresent()).isFalse();
        }
    }

    public static List<Path> walk(Path dir, FileVisitOption... options) {
        List<Path> dirContent;
        try (Stream<Path> stream = Files.walk(dir,options)) {
            dirContent = stream.collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dirContent;
    }
}
