package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.cgi.eoss.platform.core.processing.io.download.DownloaderFacade;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class DownloaderServiceTest {

    private static final String USER_UUID = "user_uuid";
    private static final String INPUT_ID = "inputId";

    private final DownloaderFacade downloaderFacade = Mockito.mock(DownloaderFacade.class);
    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    private final InOrder inOrder = Mockito.inOrder(downloaderFacade);
    private final Path baseTarget = fileSystem.getPath("baseTarget");
    private final Path inputTarget = baseTarget.resolve(INPUT_ID);

    @After
    public void afterEach() throws IOException {
        inOrder.verifyNoMoreInteractions();
        Mockito.verifyNoMoreInteractions(downloaderFacade);

        fileSystem.close();
    }

    @Test
    public void testDownloadInputs_StoresFileDirectlyInInputFolder_WhenInputHasOneUriAndCreateSubdirIsFalse() {
        String inputUriString = "https://download/input/file.txt";
        URI inputURI = URI.create(inputUriString);
        DownloadRequest downloadRequest = new DownloadRequest(inputURI, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(downloadRequest)))
                .thenReturn(ImmutableMap.of(inputURI, inputTarget.resolve("file.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, Collections.singletonList(inputUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactly(inputTarget.resolve("file.txt"));

        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(downloadRequest));
    }

    @Test
    public void testDownloadInputs_StoresFileDirectlyInInputFolder_WhenInputHasOneUriAndCreateSubdirIsTrue() {
        String inputUriString = "https://download/input/file.txt";
        URI inputURI = URI.create(inputUriString);
        DownloadRequest downloadRequest = new DownloadRequest(inputURI, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(downloadRequest)))
                .thenReturn(ImmutableMap.of(inputURI, inputTarget.resolve("file.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(true).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, Collections.singletonList(inputUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactly(inputTarget.resolve("file.txt"));

        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(downloadRequest));
    }

    @Test
    public void testDownloadInputs_StoresAllFilesInSameInputFolder_WhenInputHasMultipleUriAndCreateSubdirIsFalse() {
        String firstUriString = "https://download/input/file1.txt";
        String secondUriString = "http://download/input/file2.txt";
        URI firstUri = URI.create(firstUriString);
        URI secondUri = URI.create(secondUriString);
        DownloadRequest firstRequest = new DownloadRequest(firstUri, inputTarget, null, USER_UUID);
        DownloadRequest secondRequest = new DownloadRequest(secondUri, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.isSupportedProtocol("http")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(firstRequest, secondRequest)))
                .thenReturn(ImmutableMap.of(
                        firstUri, inputTarget.resolve("file1.txt"),
                        secondUri, inputTarget.resolve("file2.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(firstUriString, secondUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactlyInAnyOrder(
                inputTarget.resolve("file1.txt"),
                inputTarget.resolve("file2.txt"));

        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).isSupportedProtocol("http");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(firstRequest, secondRequest));
    }

    @Test
    public void testDownloadInputs_StoresFilesInSeparateSubFoldersPerUri_WhenInputHasMultipleURIAndCreateSubdirIsTrue() {
        String firstUriString = "https://download/input/file1.txt";
        String secondUriString = "ftp://download/input/file2.txt";
        URI firstUri = URI.create(firstUriString);
        URI secondUri = URI.create(secondUriString);
        Path firstSubfolder = inputTarget.resolve("file1.txt");
        Path secondSubfolder = inputTarget.resolve("file2.txt");
        DownloadRequest firstRequest = new DownloadRequest(firstUri, firstSubfolder, null, USER_UUID);
        DownloadRequest secondRequest = new DownloadRequest(secondUri, secondSubfolder, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.isSupportedProtocol("ftp")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(firstRequest, secondRequest)))
                .thenReturn(ImmutableMap.of(
                        firstUri, firstSubfolder.resolve("file1.txt"),
                        secondUri, secondSubfolder.resolve("file2.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(true).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(firstUriString, secondUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactlyInAnyOrder(
                firstSubfolder.resolve("file1.txt"),
                secondSubfolder.resolve("file2.txt"));

        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).isSupportedProtocol("ftp");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(firstRequest, secondRequest));
    }

    @Test
    public void testDownloadInputs_RemoveExtensionFromSubfolderName_WhenOneOfMultipleInputIsZipAndCreateSubdirIsTrue() {
        String filesUriString = "https://download/input/file.txt";
        String zipUriString = "https://download/input/archive.zip";
        URI filesUri = URI.create(filesUriString);
        URI zipUri = URI.create(zipUriString);
        Path filesSubfolder = inputTarget.resolve("file.txt");
        Path zipSubfolder = inputTarget.resolve("archive");
        DownloadRequest filesRequest = new DownloadRequest(filesUri, filesSubfolder, null, USER_UUID);
        DownloadRequest zipRequest = new DownloadRequest(zipUri, zipSubfolder, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(filesRequest, zipRequest)))
                .thenReturn(ImmutableMap.of(
                        filesUri, filesSubfolder.resolve("file.txt"),
                        zipUri, zipSubfolder.resolve("archive.zip")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(true).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(filesUriString, zipUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactlyInAnyOrder(
                filesSubfolder.resolve("file.txt"),
                zipSubfolder.resolve("archive.zip"));

        inOrder.verify(downloaderFacade, times(2)).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(filesRequest, zipRequest));
    }

    @Test
    public void testDownloadInputs_ThrowsUncheckedIOException_WhenTheDownloadFails() {
        String inputUriString = "https://download/input/file.txt";
        URI inputURI = URI.create(inputUriString);
        DownloadRequest downloadRequest = new DownloadRequest(inputURI, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(downloadRequest)))
                .thenThrow(new UncheckedIOException(new IOException("download failed")));

        assertThatThrownBy(() -> downloaderServiceWithCreateSubdirectories(true).downloadInputs(
                        baseTarget,
                        ImmutableList.of(createParam(INPUT_ID, Collections.singletonList(inputUriString))),
                        USER_UUID))
                .hasMessage("java.io.IOException: download failed")
                .isInstanceOf(UncheckedIOException.class);

        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(downloadRequest));
    }

    @Test
    public void testDownloadInputs_IgnoresUri_WhenInputUriIsMalformed() {
        String malformedUriString = " :::not a valid uri:::";
        String validUriString = "https://download/input/file.txt";
        URI validUri = URI.create(validUriString);
        DownloadRequest validRequest = new DownloadRequest(validUri, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(validRequest)))
                .thenReturn(ImmutableMap.of(validUri, inputTarget.resolve("file.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(malformedUriString, validUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactly(inputTarget.resolve("file.txt"));
        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(validRequest));
    }

    @Test
    public void testDownloadInputs_IgnoresUri_WhenInputUriHasNoScheme() {
        String noSchemeUriString = "download/input/file.txt";
        String validUriString = "https://download/input/file.txt";
        URI validUri = URI.create(validUriString);
        DownloadRequest validRequest = new DownloadRequest(validUri, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(validRequest)))
                .thenReturn(ImmutableMap.of(validUri, inputTarget.resolve("file.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(noSchemeUriString, validUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactly(inputTarget.resolve("file.txt"));
        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(validRequest));
    }

    @Test
    public void testDownloadInputs_IgnoresUri_WhenInputUriHasUnsupportedProtocol() {
        String unsupportedUriString = "ftp://download/input/file.txt";
        String validUriString = "https://download/input/file.txt";
        URI validUri = URI.create(validUriString);
        DownloadRequest validRequest = new DownloadRequest(validUri, inputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("ftp")).thenReturn(false);
        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(validRequest)))
                .thenReturn(ImmutableMap.of(validUri, inputTarget.resolve("file.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(unsupportedUriString, validUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).hasSize(1);
        assertThat(downloadedInputs.get(INPUT_ID)).containsExactly(inputTarget.resolve("file.txt"));

        inOrder.verify(downloaderFacade).isSupportedProtocol("ftp");
        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(validRequest));
    }

    @Test
    public void testDownloadInputs_ProducesNoResultForInput_WhenAllUrisAreInvalid() {
        String malformedUriString = " :::not a valid uri:::";
        String noSchemeUriString = "download/input/file.txt";
        String unsupportedUriString = "ftp://download/input/file.txt";

        when(downloaderFacade.isSupportedProtocol("ftp")).thenReturn(false);
        when(downloaderFacade.download(ImmutableList.of())).thenReturn(ImmutableMap.of());

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(createParam(INPUT_ID, ImmutableList.of(
                        malformedUriString, noSchemeUriString, unsupportedUriString))),
                USER_UUID);

        assertThat(downloadedInputs.isEmpty()).isTrue();
        inOrder.verify(downloaderFacade).isSupportedProtocol("ftp");
        inOrder.verify(downloaderFacade).download(ImmutableList.of());
    }

    @Test
    public void testDownloadInputs_ReturnsResultsGroupedByInputName_WhenMultipleDownloadableInputsAreProvided() {
        String firstInputId = "firstInputId";
        String secondInputId = "secondInputId";
        Path firstInputTarget = baseTarget.resolve(firstInputId);
        Path secondInputTarget = baseTarget.resolve(secondInputId);
        String firstUriString = "https://download/input/file1.txt";
        String secondUriString = "ftp://download/input/file2.txt";
        URI firstUri = URI.create(firstUriString);
        URI secondUri = URI.create(secondUriString);
        DownloadRequest firstRequest = new DownloadRequest(firstUri, firstInputTarget, null, USER_UUID);
        DownloadRequest secondRequest = new DownloadRequest(secondUri, secondInputTarget, null, USER_UUID);

        when(downloaderFacade.isSupportedProtocol("https")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(firstRequest)))
                .thenReturn(ImmutableMap.of(firstUri, firstInputTarget.resolve("file1.txt")));
        when(downloaderFacade.isSupportedProtocol("ftp")).thenReturn(true);
        when(downloaderFacade.download(ImmutableList.of(secondRequest)))
                .thenReturn(ImmutableMap.of(secondUri, secondInputTarget.resolve("file2.txt")));

        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget,
                ImmutableList.of(
                        createParam(firstInputId, Collections.singletonList(firstUriString)),
                        createParam(secondInputId, Collections.singletonList(secondUriString))),
                USER_UUID);

        assertThat(downloadedInputs.keySet()).containsExactlyInAnyOrder(firstInputId, secondInputId);
        assertThat(downloadedInputs.get(firstInputId)).containsExactly(firstInputTarget.resolve("file1.txt"));
        assertThat(downloadedInputs.get(secondInputId)).containsExactly(secondInputTarget.resolve("file2.txt"));

        inOrder.verify(downloaderFacade).isSupportedProtocol("https");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(firstRequest));
        inOrder.verify(downloaderFacade).isSupportedProtocol("ftp");
        inOrder.verify(downloaderFacade).download(ImmutableList.of(secondRequest));
    }

    @Test
    public void testDownloadInputs_ReturnsEmptyResult_WhenNoDownloadableInputsProvided() {
        Multimap<String, Path> downloadedInputs = downloaderServiceWithCreateSubdirectories(false).downloadInputs(
                baseTarget, ImmutableList.of(), USER_UUID);

        assertThat(downloadedInputs.isEmpty()).isTrue();
    }

    private K8sJobParams.DownloadableParam createParam(String inputName, List<String> values) {
        return K8sJobParams.DownloadableParam
                .builder()
                .paramName(inputName)
                .values(values)
                .build();
    }

    private DownloaderService downloaderServiceWithCreateSubdirectories(boolean createSubdirectories) {
        return  new DownloaderService(downloaderFacade, createSubdirectories);
    }
}
