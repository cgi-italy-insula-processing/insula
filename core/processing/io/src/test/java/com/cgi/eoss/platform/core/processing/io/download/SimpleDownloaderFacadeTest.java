package com.cgi.eoss.platform.core.processing.io.download;

import com.cgi.eoss.platform.core.processing.io.ServiceIoException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

public class SimpleDownloaderFacadeTest {


    private final Downloader httpDownloader = Mockito.mock(Downloader.class);
    private final Downloader ftpDownloader = Mockito.mock(Downloader.class);
    private final Downloader brokenHttpDownloader =  Mockito.mock(Downloader.class);
    private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    private InOrder inOrder;

    @Before
    public void setUp() {
        inOrder = inOrder(httpDownloader, ftpDownloader, brokenHttpDownloader);
    }

    @After
    public void tearDown() throws IOException {
        fs.close();
        inOrder.verifyNoMoreInteractions();
        Mockito.verifyNoMoreInteractions(httpDownloader, ftpDownloader, brokenHttpDownloader);
    }

    @Test
    public void testDownload_ThrowsServiceIoException_WhenDownloadersListIsEmpty() {
        Path downloadFolder = fs.getPath("downloads");
        assertThatThrownBy(() ->
                simpleDownloaderFacade().download(request("http://test/uri", fs.getPath("downloads"))))
                        .isInstanceOf(ServiceIoException.class)
                        .hasMessage("No downloader was able to process the URI: http://test/uri");
        assertThat(Files.exists(downloadFolder)).isFalse();
    }

    @Test
    public void testDownload_ThrowsServiceIoException_WhenDownloadersDoNotSupportProtocol() {
        Path downloadFolder = fs.getPath("downloads");
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));

        assertThatThrownBy(() -> simpleDownloaderFacade(httpDownloader, ftpDownloader)
                        .download(request("nfs://test/uri", fs.getPath("downloads"))))
                .isInstanceOf(ServiceIoException.class)
                .hasMessage("No downloader was able to process the URI: nfs://test/uri" );
        assertThat(Files.exists(downloadFolder)).isFalse();

        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(ftpDownloader).getProtocols();
    }

    @Test
    public void testDownload_ThrowsServiceIoException_WhenAllSelectedDownloadersFail() throws Exception {
        Path downloadFolder = fs.getPath("downloads");
        DownloadRequest request = request("http://test/uri", downloadFolder);
        when(brokenHttpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));

        ArgumentCaptor<DownloadRequest> brokenRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(brokenHttpDownloader.download(brokenRequestCaptor.capture()))
                .thenThrow(new IOException("test-exception1"));

        ArgumentCaptor<DownloadRequest> requestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(requestCaptor.capture()))
                .thenThrow(new IOException("test-exception2"));

        assertThatThrownBy(() -> simpleDownloaderFacade(brokenHttpDownloader, httpDownloader).download(request))
                .isInstanceOf(ServiceIoException.class)
                .hasMessage("No downloader was able to process the URI: http://test/uri");

        assertThat(Files.exists(downloadFolder)).isFalse();

        inOrder.verify(brokenHttpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(brokenHttpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), request.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), request.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder().getParent(), downloadFolder)
        ));
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), request.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), request.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder().getParent(), downloadFolder)
        ));
    }

    @Test
    public void testDownload_CreatesDownloadDirAndReturnsDownloadPath_WhenSingleDownloaderIsSelected() throws Exception {
        Path downloadFolder = fs.getPath("downloads");
        DownloadRequest request = request("http://test/uri", downloadFolder);
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));

        ArgumentCaptor<DownloadRequest> requestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(requestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("file.txt"));
                });

        assertThat(simpleDownloaderFacade(httpDownloader, ftpDownloader).download(request))
                .isEqualTo(downloadFolder.resolve("file.txt"));
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("file.txt")
        );

        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempRequest ->
            Objects.equals(tempRequest.getDownloadUri(), request.getDownloadUri())
            && Objects.equals(tempRequest.getSubsetting(),  request.getSubsetting())
            && Objects.equals(tempRequest.getDownloadFolder(), requestCaptor.getValue().getDownloadFolder())
        ));
    }

    @Test
    public void testDownload_CreatesDownloadDirAndReturnsDownloadPath_WhenFirstSelectedDownloaderThrowsExceptionAndSecondSucceeds() throws Exception {
        Path downloadFolder = fs.getPath("downloads");
        DownloadRequest request = request("http://test/uri", downloadFolder);
        Path expectedFile = downloadFolder.resolve("file.txt");
        when(brokenHttpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));

        ArgumentCaptor<DownloadRequest> brokenRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(brokenHttpDownloader.download(brokenRequestCaptor.capture()))
                .thenThrow(new IOException("test-exception"));

        ArgumentCaptor<DownloadRequest> requestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(requestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("file.txt"));
                });

        assertThat(simpleDownloaderFacade(brokenHttpDownloader, httpDownloader).download(request))
                .isEqualTo(expectedFile);
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("file.txt")
        );

        inOrder.verify(brokenHttpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(brokenHttpDownloader).download(argThat(tempRequest ->
                Objects.equals(tempRequest.getDownloadUri(), request.getDownloadUri())
                && Objects.equals(tempRequest.getSubsetting(), request.getSubsetting())
                && Objects.equals(tempRequest.getDownloadFolder(), brokenRequestCaptor.getValue().getDownloadFolder())
        ));
        inOrder.verify(httpDownloader).download(argThat(tempRequest ->
                Objects.equals(tempRequest.getDownloadUri(), request.getDownloadUri())
                        && Objects.equals(tempRequest.getSubsetting(), request.getSubsetting())
                        && Objects.equals(tempRequest.getDownloadFolder(), requestCaptor.getValue().getDownloadFolder())
        ));
    }

    @Test
    public void testDownload_CreatesDownloadDirAndReturnsDownloadPath_WhenSelectedDownloaderSupportsMultipleProtocols() throws Exception {
        Path downloadFolder = fs.getPath("downloads");
        DownloadRequest request = request("https://test/uri", downloadFolder);
        Path expectedFile = downloadFolder.resolve("file.txt");
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http", "https"));

        ArgumentCaptor<DownloadRequest> requestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(requestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("file.txt"));
                });

        assertThat(simpleDownloaderFacade(httpDownloader).download(request)).isEqualTo(expectedFile);
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("file.txt")
        );

        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), request.getDownloadUri())
                && Objects.equals(tempDownloadRequest.getSubsetting(), request.getSubsetting())
                && Objects.equals(tempDownloadRequest.getDownloadFolder(), requestCaptor.getValue().getDownloadFolder())
        ));
    }

    @Test
    public void testDownload_DownloadsAllRequestsAndCreatesDownloadDirs_WhenAllRequestsAreSupportedBySameDownloader() throws Exception {
        Path downloadFolderOne = fs.getPath("downloads/downloadsOne");
        Path downloadFolderTwo = fs.getPath("downloads/downloadsTwo");
        Path expectedFileOne = downloadFolderOne.resolve("fileOne.txt");
        Path expectedFileTwo = downloadFolderTwo.resolve("fileTwo.txt");
        String uriOne = "http://test/uriOne";
        String uriTwo = "http://test/uriTwo";
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));
        DownloadRequest requestOne = request(uriOne, downloadFolderOne);
        DownloadRequest requestTwo = request(uriTwo, downloadFolderTwo);

        ArgumentCaptor<DownloadRequest> httpRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(httpRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest captured = invocation.getArgument(0);
                    return Files.createFile(captured.getDownloadFolder().resolve("fileOne.txt"));
                })
                .thenAnswer(invocation -> {
                    DownloadRequest captured = invocation.getArgument(0);
                    return Files.createFile(captured.getDownloadFolder().resolve("fileTwo.txt"));
                });

        assertThat(simpleDownloaderFacade(ftpDownloader, httpDownloader).download(
                        ImmutableList.of(requestOne, requestTwo)))
                .isEqualTo(ImmutableMap.of(
                        URI.create(uriOne), expectedFileOne,
                        URI.create(uriTwo), expectedFileTwo));
        assertThat(expectedFileOne).isEmptyFile();
        assertThat(expectedFileTwo).isEmptyFile();
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("downloadsOne"),
                fs.getPath("downloads").resolve("downloadsTwo"),
                fs.getPath("downloads").resolve("downloadsOne").resolve("fileOne.txt"),
                fs.getPath("downloads").resolve("downloadsTwo").resolve("fileTwo.txt")
        );

        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), requestOne.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), requestOne.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), httpRequestCaptor.getAllValues().get(0).getDownloadFolder())
        ));
        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), requestTwo.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), requestTwo.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), httpRequestCaptor.getAllValues().get(1).getDownloadFolder())
        ));
    }

    @Test
    public void testDownload_DownloadsAllRequestsAndCreatesDownloadDirs_WhenRequestsAreSupportedByDifferentDownloaders() throws Exception {
        Path downloadHttp = fs.getPath("downloads/downloadHttp");
        Path downloadFtp = fs.getPath("downloads/downloadFtp");
        Path expectedFileHttp =  downloadHttp.resolve("http.txt");
        Path expectedFileFtp = downloadFtp.resolve("ftp.txt");
        String httpUri = "http://test/uriHttp";
        String ftpUri = "ftp://test/uriFtp";
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));
        DownloadRequest httpRequest = request(httpUri, downloadHttp);
        DownloadRequest ftpRequest = request(ftpUri, downloadFtp);

        ArgumentCaptor<DownloadRequest> httpRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(httpRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("http.txt"));
                });

        ArgumentCaptor<DownloadRequest> ftpRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(ftpDownloader.download(ftpRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("ftp.txt"));
                });

        assertThat(simpleDownloaderFacade(ftpDownloader, httpDownloader)
                        .download(ImmutableList.of(httpRequest, ftpRequest)))
                .isEqualTo(ImmutableMap.of(
                        URI.create(httpUri),  expectedFileHttp,
                        URI.create(ftpUri), expectedFileFtp));
        assertThat(expectedFileHttp).isEmptyFile();
        assertThat(expectedFileFtp).isEmptyFile();
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("downloadHttp"),
                fs.getPath("downloads").resolve("downloadFtp"),
                fs.getPath("downloads").resolve("downloadHttp").resolve("http.txt"),
                fs.getPath("downloads").resolve("downloadFtp").resolve("ftp.txt")
        );

        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), httpRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), httpRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), httpRequestCaptor.getValue().getDownloadFolder())
        ));
        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(ftpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), ftpRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), ftpRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), ftpRequestCaptor.getValue().getDownloadFolder())
        ));
    }

    @Test
    public void testDownload_DownloadsAllRequestsInSameTargetDir_WhenRequestsHaveSameTargetDir() throws Exception {
        Path downloadBasePath = fs.getPath("downloads");
        Path expectedFileHttp =  downloadBasePath.resolve("http.txt");
        Path expectedFileFtp = downloadBasePath.resolve("ftp.txt");
        String httpUri = "http://test/uriHttp";
        String ftpUri = "ftp://test/uriFtp";
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));
        DownloadRequest httpRequest = request(httpUri, downloadBasePath);
        DownloadRequest ftpRequest = request(ftpUri, downloadBasePath);

        ArgumentCaptor<DownloadRequest> httpRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(httpRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("http.txt"));
                });

        ArgumentCaptor<DownloadRequest> ftpRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(ftpDownloader.download(ftpRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("ftp.txt"));
                });

        assertThat(simpleDownloaderFacade(ftpDownloader, httpDownloader)
                .download(ImmutableList.of(httpRequest, ftpRequest)))
                .isEqualTo(ImmutableMap.of(
                        URI.create(httpUri),  expectedFileHttp,
                        URI.create(ftpUri), expectedFileFtp));
        assertThat(expectedFileHttp).isEmptyFile();
        assertThat(expectedFileFtp).isEmptyFile();
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("http.txt"),
                fs.getPath("downloads").resolve("ftp.txt")
        );

        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), httpRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), httpRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), httpRequestCaptor.getValue().getDownloadFolder()))
        );
        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(ftpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), ftpRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), ftpRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), ftpRequestCaptor.getValue().getDownloadFolder()))
        );
    }

    @Test
    public void testDownload_DownloadsAllRequestsInSameTargetDir_WhenRequestsHaveSameTargetAndSecondRequestIsDownloadedWithSecondDownloaderAttempt() throws Exception {
        Path downloadBasePath = fs.getPath("downloads");
        Path expectedFirstFile =  downloadBasePath.resolve("firstHttp.txt");
        Path expectedSecondAttemptFile = downloadBasePath.resolve("secondHttp.txt");
        String firstUri = "http://test/firstUri";
        String secondAttemptUri = "http://test/secondAttemptUri";
        when(brokenHttpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        DownloadRequest firstRequest = request(firstUri, downloadBasePath);
        DownloadRequest secondAttemptRequest = request(secondAttemptUri, downloadBasePath);

        ArgumentCaptor<DownloadRequest> firstRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(brokenHttpDownloader.download(firstRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("firstHttp.txt"));}
                )
                .thenThrow(new ServiceIoException("fail first attempt for second attempt request exception"));

        ArgumentCaptor<DownloadRequest> secondAttemptRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(secondAttemptRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("secondHttp.txt"));}
                );

        assertThat(simpleDownloaderFacade(brokenHttpDownloader, httpDownloader)
                .download(ImmutableList.of(firstRequest, secondAttemptRequest)))
                .isEqualTo(ImmutableMap.of(
                        URI.create(firstUri),  expectedFirstFile,
                        URI.create(secondAttemptUri), expectedSecondAttemptFile));
        assertThat(expectedFirstFile).isEmptyFile();
        assertThat(expectedSecondAttemptFile).isEmptyFile();
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("firstHttp.txt"),
                fs.getPath("downloads").resolve("secondHttp.txt")
        );
        inOrder.verify(brokenHttpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(brokenHttpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), firstRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), firstRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), firstRequestCaptor.getAllValues().get(0).getDownloadFolder()))
        );
        inOrder.verify(brokenHttpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(brokenHttpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), secondAttemptRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), secondAttemptRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), firstRequestCaptor.getAllValues().get(1).getDownloadFolder()))
        );
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
                Objects.equals(tempDownloadRequest.getDownloadUri(), secondAttemptRequest.getDownloadUri())
                        && Objects.equals(tempDownloadRequest.getSubsetting(), secondAttemptRequest.getSubsetting())
                        && Objects.equals(tempDownloadRequest.getDownloadFolder(), secondAttemptRequestCaptor.getValue().getDownloadFolder()))
        );
    }

    @Test
    public void testDownload_ThrowsServiceIoException_WhenOneRequestIsSupportedAndTheOtherNot() throws Exception {
        Path supportedDownloadFolder = fs.getPath("downloads/supported");
        Path unsupportedDownloadFolder = fs.getPath("downloads/unsupported");
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));
        DownloadRequest supportedRequest = request("http://test/uriSupported", supportedDownloadFolder);

        ArgumentCaptor<DownloadRequest> supportedRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        when(httpDownloader.download(supportedRequestCaptor.capture()))
                .thenAnswer(invocation -> {
                    DownloadRequest capturedRequest = invocation.getArgument(0);
                    return Files.createFile(capturedRequest.getDownloadFolder().resolve("http.txt"));
                });

        assertThatThrownBy(() -> simpleDownloaderFacade(ftpDownloader, httpDownloader)
                .download(ImmutableList.of(
                        supportedRequest,
                        request("unsupported://test/uriNotSupported", unsupportedDownloadFolder))))
                .isInstanceOf(ServiceIoException.class)
                .hasMessage("No downloader was able to process the URI: unsupported://test/uriNotSupported");
        assertThat(supportedDownloadFolder.resolve("http.txt")).isEmptyFile();
        assertThat(walk(fs.getPath("downloads"))).containsExactlyInAnyOrder(
                fs.getPath("downloads"),
                fs.getPath("downloads").resolve("supported"),
                fs.getPath("downloads").resolve("supported").resolve("http.txt")
        );

        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(httpDownloader).download(argThat(tempDownloadRequest ->
            Objects.equals(tempDownloadRequest.getDownloadUri(), supportedRequest.getDownloadUri())
                    && Objects.equals(tempDownloadRequest.getSubsetting(), supportedRequest.getSubsetting())
                    && Objects.equals(tempDownloadRequest.getDownloadFolder(), supportedRequestCaptor.getValue().getDownloadFolder()))
        );
        inOrder.verify(ftpDownloader).getProtocols();
        inOrder.verify(httpDownloader).getProtocols();
    }

    @Test
    public void testDownload_ReturnsEmptyMap_WhenRequestListIsEmpty() {
        assertThat(simpleDownloaderFacade(httpDownloader).download(ImmutableList.of()))
                .isEqualTo(ImmutableMap.of());
    }

    @Test
    public void testIsSupportedProtocol_ReturnsTrue_WhenProtocolSchemeIsSupported() {
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));

        assertThat(simpleDownloaderFacade(httpDownloader, ftpDownloader).isSupportedProtocol("ftp"))
                .isTrue();

        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(ftpDownloader).getProtocols();
    }

    @Test
    public void testIsSupportedProtocol_ReturnsFalse_WhenProtocolSchemeIsNotSupported() {
        when(httpDownloader.getProtocols()).thenReturn(ImmutableSet.of("http"));
        when(ftpDownloader.getProtocols()).thenReturn(ImmutableSet.of("ftp"));

        assertThat(simpleDownloaderFacade(httpDownloader, ftpDownloader).isSupportedProtocol("nfs"))
                .isFalse();

        inOrder.verify(httpDownloader).getProtocols();
        inOrder.verify(ftpDownloader).getProtocols();
    }

    @Test
    public void testIsSupportedProtocol_ReturnsFalse_WhenNoDownloadersAreConfigured() {
        assertThat(simpleDownloaderFacade().isSupportedProtocol("http")).isFalse();
    }

    @Test
    public void testCleanUp_ThrowsUnsupportedOperationException() {
        assertThatThrownBy(() -> simpleDownloaderFacade().cleanUp(URI.create("http://test/cleanUp")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Cannot clean up 'http://test/cleanUp', operation not implemented");
    }

    private static SimpleDownloaderFacade simpleDownloaderFacade(Downloader... downloaders) {
        return new SimpleDownloaderFacade(new LinkedHashSet<>(Arrays.asList(downloaders)));
    }

    private static DownloadRequest request(String uri, Path target) {
        return new DownloadRequest(URI.create(uri), target, null);
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