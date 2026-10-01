package com.cgi.eoss.platform.core.processing.io.download.s3;

import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3AccountProperties;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3DownloaderCoreConfiguration;
import com.cgi.eoss.platform.core.processing.io.download.s3.config.S3Location;
import com.google.common.collect.ImmutableSet;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class S3CoreDownloaderTest {

    private static final String USER_UUID = "undefined";

    private Path outputDir;
    private URI pathStyleUri;
    private URI vHostStyleUri;
    private URI pathStyleNoRegionUri;
    private URI vHostStyleNoRegionUri;
    private String s3Host;
    private String s3Ip;
    private int s3Port;
    private MockWebServer webServer;
    private S3CoreDownloader downloader;

    @Before
    public void init() throws IOException {

        outputDir = initTmpFolder("s3");

        webServer = new MockWebServer();
        webServer.start(InetAddress.getLoopbackAddress(), 0);
        s3Host = webServer.getHostName();
        s3Port = webServer.getPort();
        s3Ip = hostNameToIp(s3Host);
        pathStyleUri = URI.create("http://" + s3Host + ":" + s3Port + "/bucketOne");
        vHostStyleUri = URI.create("http://bucketTwo." + s3Host + ":" + s3Port);
        pathStyleNoRegionUri = URI.create("http://" + s3Host + ":" + s3Port + "/bucketThree");
        vHostStyleNoRegionUri = URI.create("http://bucketFour." + s3Host + ":" + s3Port);

        S3DownloaderCoreConfiguration configuration = initConfigurationFixture();

        downloader = new S3CoreDownloader(configuration);
    }

    @After
    public void shutdown() throws IOException {
        webServer.shutdown();
    }

    @Test
    public void testDownloadFromPathStyleUriWithRegion() throws IOException, InterruptedException {
        final String objectKey = "objectKey";

        URI uri = URI.create(pathStyleUri.toASCIIString() + "/" + objectKey);

        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("test"));

        Path downloadedObject = downloader.download(new DownloadRequest(uri, outputDir, null, USER_UUID));
        assertFileContent(downloadedObject, "test");

        assertThat(webServer.getRequestCount(), is(1));

        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod(), is("GET"));
            assertThat(request.getRequestUrl().encodedPath(), is("/bucketOne/objectKey"));
            assertThat(request.getRequestUrl().scheme(), is("http"));
            assertS3Host(request);
            assertThat(request.getRequestUrl().port(), is(s3Port));
            assertThat(request.getHeader("Host"), is(s3Host + ":" + s3Port));
            assertThat(request.getHeader("Authorization"), startsWith("AWS4-HMAC-SHA256 Credential=accessKey/" + now() + "/italy/s3/aws4_request"));
        }
    }

    @Test
    @Ignore("TODO: AmazonS3 client fails to generate VHost style url in local")
    public void testDownloadFromVHostStyleUriWithRegion() throws IOException, InterruptedException {
        final String objectKey = "objectKey";

        URI uri = URI.create(vHostStyleUri.toASCIIString() + "/" + objectKey);

        webServer.enqueue(new MockResponse().setResponseCode(200).setBody("test"));

        Path downloadedObject = downloader.download(new DownloadRequest(uri, outputDir, null, USER_UUID));
        assertFileContent(downloadedObject, "test");

        assertThat(webServer.getRequestCount(), is(1));

        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod(), is("GET"));
            assertThat(request.getRequestUrl().encodedPath(), is("/objectKey"));
            assertThat(request.getRequestUrl().scheme(), is("http"));
            assertS3Host(request);
            assertThat(request.getRequestUrl().port(), is(s3Port));
            assertThat(request.getHeader("Host"), is("bucketTwo." + s3Host + ":" + s3Port));
            assertThat(request.getHeader("Authorization"), startsWith("AWS4-HMAC-SHA256 Credential=accessKey/" + now() + "/italy/s3/aws4_request"));
        }
    }

    @Test
    public void testDownloadFromPathStyleUriWithoutRegion() throws IOException, InterruptedException {
        final String objectKey = "objectKey";

        URI uri = URI.create(pathStyleNoRegionUri.toASCIIString() + "/" + objectKey);

        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("test"));

        Path downloadedObject = downloader.download(new DownloadRequest(uri, outputDir, null, USER_UUID));
        assertFileContent(downloadedObject, "test");

        assertThat(webServer.getRequestCount(), is(1));

        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod(), is("GET"));
            assertThat(request.getRequestUrl().encodedPath(), is("/bucketThree/objectKey"));
            assertThat(request.getRequestUrl().scheme(), is("http"));
            assertS3Host(request);
            assertThat(request.getRequestUrl().port(), is(s3Port));
            assertThat(request.getHeader("Host"), is(s3Host + ":" + s3Port));
            assertThat(request.getHeader("Authorization"), startsWith("AWS4-HMAC-SHA256 Credential=accessKey/" + now() + "//s3/aws4_request"));
        }
    }

    @Test
    public void testDownloadFromPathStyleUriWithNestedKey() throws IOException, InterruptedException {
        final String objectKey = "nested/objectKey";

        URI uri = URI.create(pathStyleUri.toASCIIString() + "/" + objectKey);

        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("test"));

        Path downloadedObject = downloader.download(new DownloadRequest(uri, outputDir, null, USER_UUID));
        assertFileContent(downloadedObject, "test");

        assertThat(webServer.getRequestCount(), is(1));

        {
            RecordedRequest request = webServer.takeRequest();
            assertThat(request.getMethod(), is("GET"));
            assertThat(request.getRequestUrl().encodedPath(), is("/bucketOne/nested/objectKey"));
            assertThat(request.getRequestUrl().scheme(), is("http"));
            assertS3Host(request);
            assertThat(request.getRequestUrl().port(), is(s3Port));
            assertThat(request.getHeader("Host"), is(s3Host + ":" + s3Port));
            assertThat(request.getHeader("Authorization"), startsWith("AWS4-HMAC-SHA256 Credential=accessKey/" + now() + "/italy/s3/aws4_request"));
        }
    }

    @Test
    public void testDownloadFolderNotAllowed() throws IOException {
        final String objectKey = "objectKey/";

        URI uri = URI.create(pathStyleUri.toASCIIString() + "/" + objectKey);

        try {
            downloader.download(new DownloadRequest(uri, outputDir, null, USER_UUID));
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), is("Download of multiple objects is not implemented: objectKey/"));
        }
    }

    @Test
    public void testSupportedProtocols() {
        assertThat(downloader.getProtocols(), is(ImmutableSet.of("http", "https", "s3")));
    }

    @Test
    public void testGetPriority_ReturnsDefaultValue() {
        Assertions.assertThat(downloader.getPriority(URI.create("random-test-uri"))).isEqualTo(0);
    }

    private void assertS3Host(RecordedRequest request) {
        assertThat(request.getRequestUrl().host(), anyOf(is(s3Ip), is(s3Host)));

    }

    private void assertFileContent(Path path, String content) throws IOException {
        assertTrue(Files.exists(path));
        assertThat(new String(Files.readAllBytes(path)), is("test"));
    }

    private S3DownloaderCoreConfiguration initConfigurationFixture() {
        S3DownloaderCoreConfiguration configuration = new S3DownloaderCoreConfiguration();
        {
            S3AccountProperties account = new S3AccountProperties();
            account.setAccessKey("accessKey");
            account.setPrivateKey("privateKey");
            {
                S3Location locationPathStyle = new S3Location();
                locationPathStyle.setBaseUrl(pathStyleUri);
                locationPathStyle.setEndpoint("http://" + s3Host + ":" + s3Port);
                locationPathStyle.setRegion("italy");
                locationPathStyle.setPathStyle(true);
                locationPathStyle.setBucket("bucketOne");
                account.getLocations().add(locationPathStyle);
            }
            {
                S3Location locationVHostStyle = new S3Location();
                locationVHostStyle.setBaseUrl(vHostStyleUri);
                locationVHostStyle.setEndpoint("http://" + s3Host + ":" + s3Port);
                locationVHostStyle.setRegion("france");
                locationVHostStyle.setPathStyle(false);
                locationVHostStyle.setBucket("bucketTwo");
                account.getLocations().add(locationVHostStyle);
            }
            {
                S3Location locationPathStyleNoRegion = new S3Location();
                locationPathStyleNoRegion.setBaseUrl(pathStyleNoRegionUri);
                locationPathStyleNoRegion.setEndpoint("http://" + s3Host + ":" + s3Port);
                locationPathStyleNoRegion.setPathStyle(true);
                locationPathStyleNoRegion.setBucket("bucketThree");
                account.getLocations().add(locationPathStyleNoRegion);
            }
            {
                S3Location locationVHostStyleNoRegion = new S3Location();
                locationVHostStyleNoRegion.setBaseUrl(vHostStyleNoRegionUri);
                locationVHostStyleNoRegion.setEndpoint("http://" + s3Host + ":" + s3Port);
                locationVHostStyleNoRegion.setPathStyle(false);
                locationVHostStyleNoRegion.setBucket("bucketFour");
                account.getLocations().add(locationVHostStyleNoRegion);
            }
            configuration.getAccounts().add(account);
        }
        return configuration;
    }

    private static Path initTmpFolder(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create tmp dir");
        }
    }

    private static String hostNameToIp(String hostname) {

        try {
            return InetAddress.getByName(hostname).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Failed to convert host to ip: " + hostname);
        }
    }

    private static String now() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"));
    }
}