package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacCatalog;
import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacDocument;
import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacLink;
import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.cgi.eoss.platform.core.processing.io.download.DownloaderFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class StageInServiceTest {

    private final Path baseTestDir = Paths.get("tmpStageInTestDirectory");

    private StageInService stageInService;

    private DownloaderFacade mockDownloaderFacade;

    private ObjectMapper mockObjectMapper;

    private Path stacDocumentTempFolder;

    private Path testPath;

    private InOrder inOrder;
private static final String USER_UUID = "jobOwnerOne";

    @Before
    public void setUp() {
        cleanTestFiles();
        testPath = Paths.get(baseTestDir.toString(), "testDir", UUID.randomUUID().toString());
        try {
            Files.createDirectories(testPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mockDownloaderFacade = mock(DownloaderFacade.class);
        mockObjectMapper = mock(ObjectMapper.class);
        stacDocumentTempFolder = Paths.get(testPath.toString(), "stac");
        stageInService = new StageInService(mockDownloaderFacade, mockObjectMapper, stacDocumentTempFolder);

        inOrder = inOrder(mockDownloaderFacade, mockObjectMapper);
    }

    @After
    public void shutdown() {
        inOrder.verifyNoMoreInteractions();
        cleanTestFiles();
    }

    @Test
    public void testStageInInput_CreatesStacDocumentTempFolderWithoutCreatingOtherStageInAndAssetsDirectories_WhenStacDocumentTempFolderDoesNotExistsAndJobInputsAreEmpty() throws Exception {

        Path stageInDir = Paths.get(testPath.toString(), "stageInDir");
        Path assetsDir = Paths.get(testPath.toString(), "assetsDir");
        Multimap<String, String> inputs = ArrayListMultimap.create();
        assertThat(Files.exists(stacDocumentTempFolder)).isFalse();
        stageInService.stageInInputs(stageInDir,assetsDir,inputs,USER_UUID);
        assertThat(Files.exists(stacDocumentTempFolder)).isTrue();
        assertThat(Files.exists(stageInDir)).isFalse();
        assertThat(Files.exists(assetsDir)).isFalse();

    }

    @Test
    public void testStageInInput_DoesNotCreateStageInAndAssetsDirectories_WhenStacDocumentTempFolderExistsAndJobInputsAreEmpty() throws Exception {

        Path stageInDir = Paths.get(testPath.toString(), "stageInDir");
        Path assetsDir = Paths.get(testPath.toString(), "assetsDir");
        Multimap<String, String> inputs = ArrayListMultimap.create();
        Files.createDirectories(stacDocumentTempFolder);
        assertThat(Files.exists(stacDocumentTempFolder)).isTrue();
        assertThat(Files.exists(stageInDir)).isFalse();
        assertThat(Files.exists(assetsDir)).isFalse();
        stageInService.stageInInputs(stageInDir,assetsDir,inputs,USER_UUID);
        assertThat(Files.exists(stacDocumentTempFolder)).isTrue();
        assertThat(Files.exists(stageInDir)).isFalse();
        assertThat(Files.exists(assetsDir)).isFalse();

    }

    @Test
    public void testStageInInput_ThrowsNoSuchFileExceptionCreatingStacTempFolder_WhenBaseDirectoryDoesNotExists() throws Exception {

        Files.deleteIfExists(testPath);
        Path stageInDir = Paths.get(testPath.toString(), "stageInDir");
        Path assetsDir = Paths.get(testPath.toString(), "assetsDir");
        Multimap<String, String> inputs = ArrayListMultimap.create();
        assertThat(Files.exists(stacDocumentTempFolder)).isFalse();
        assertThat(Files.exists(testPath)).isFalse();
        assertThatThrownBy( () -> stageInService.stageInInputs(stageInDir,assetsDir,inputs,USER_UUID))
                .isInstanceOf(NoSuchFileException.class)
                .hasMessage(stacDocumentTempFolder.toString());
        assertThat(Files.exists(stacDocumentTempFolder)).isFalse();
        assertThat(Files.exists(testPath)).isFalse();

    }

    @Test
    public void testStageInInput_CreatesDownloadRequestWithUserUuid_WhenUserUuidIsNotNull() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        Path stageInDir = Paths.get(testPath.toString(), "stageInDir");
        Path assetsDir = Paths.get(testPath.toString(), "assetsDir");
        Multimap<String, String> inputs = ArrayListMultimap.create();
        String fileInputKey = "inputKey";
        inputs.put(fileInputKey, "example.com");

        //prepare StacDocument download
        URI downloadURI = new URI("example.com");
        Path destFilePath = Paths.get(stacDocumentTempFolder.toString(), fileInputKey);

        Path baseDownloadPath = Files.createDirectories(Paths.get(testPath.toString(),"expectedDownloadPath"));
        String stacFileName = "testFile.stac";
        Path downloadedFile = Files.createFile(baseDownloadPath.resolve(stacFileName));

        //prepare StacDocument DownloadRequest with userUuid
        when(mockDownloaderFacade.download(eq(new DownloadRequest(downloadURI, destFilePath, null, USER_UUID))))
                .thenReturn(baseDownloadPath);

        //create downloaded StacDocument object
        String stacItemId = "stacItemID";
        String assetKey = "assetKey";
        String assetHref = "example.com/asset";
        StacDocument stacDocument = new StacDocument();
        Map<String, StacDocument.Asset> assets = new HashMap<>();
        StacDocument.Asset asset = new StacDocument.Asset();
        asset.setHref(assetHref);
        assets.put(assetKey, asset);
        StacDocument.StacItem stacItem = new StacDocument.StacItem();
        stacItem.setId(stacItemId);
        stacItem.setAssets(assets);
        List<StacDocument.StacItem> items = new ArrayList<>();
        items.add(stacItem);
        stacDocument.setItems(items);

        when(mockObjectMapper.readValue(Files.readAllBytes(downloadedFile), StacDocument.class))
                .thenReturn(stacDocument);

        //fake downloaded assets
        Path baseAssetDownloadPath = Files.createDirectories(Paths.get(testPath.toString(), assetKey));
        String assetFileName = "downloadedAsset.json";
        Files.createFile(baseAssetDownloadPath.resolve(assetFileName));

        Path assetDestFilePath = Paths.get(assetsDir.toString(), fileInputKey, stacItemId, assetKey);

        URI assetDownloadURI = new URI(assetHref);
        //prepare assets DownloadRequest with userUuid
        when(mockDownloaderFacade.download(eq(
                new DownloadRequest(assetDownloadURI, assetDestFilePath, null, USER_UUID)))
        ).thenReturn(baseAssetDownloadPath);

        when(mockObjectMapper.writeValueAsBytes(eq(stacItem))).thenReturn(mapper.writeValueAsBytes(stacItem));

        //prepare StacCatalog object
        List<StacLink> stacLinks = new ArrayList<>();
        Path stacItemJsonPath = Paths.get(stageInDir.toAbsolutePath().toString(), fileInputKey, stacItemId + ".json");
        stacLinks.add(StacLink.builder()
                .type("application/geo+json")
                .rel("item")
                .title(stacItemId)
                .href(stacItemJsonPath.toString())
                .build());
        StacCatalog stacCatalog = StacCatalog.builder()
                .id(fileInputKey)
                .type("Catalog")
                .stacVersion("1.0.0")
                .description("Root catalog for input " + fileInputKey)
                .links(stacLinks)
                .build();
        when(mockObjectMapper.writeValueAsString(eq(stacCatalog))).thenReturn(mapper.writeValueAsString(stacCatalog));

        stageInService.stageInInputs(stageInDir,assetsDir,inputs,USER_UUID);

        assertThat(stacItemJsonPath).exists();
        assertThat(Paths.get(stageInDir.toAbsolutePath().toString(), fileInputKey, "catalog.json")).exists();

        ArgumentCaptor<DownloadRequest> documentRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        inOrder.verify(mockDownloaderFacade).download(documentRequestCaptor.capture());
        DownloadRequest actualDocumentRequest = documentRequestCaptor.getValue();
        assertThat(actualDocumentRequest.getDownloadFolder()).isEqualTo(destFilePath);
        assertThat(actualDocumentRequest.getDownloadUri()).isEqualTo(downloadURI);
        assertThat(actualDocumentRequest.getSubsetting()).isNull();
        assertThat(actualDocumentRequest.getUserUuid()).isEqualTo(USER_UUID);

        inOrder.verify(mockObjectMapper).readValue(Files.readAllBytes(downloadedFile), StacDocument.class);

        ArgumentCaptor<DownloadRequest> assetRequestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        inOrder.verify(mockDownloaderFacade).download(assetRequestCaptor.capture());
        DownloadRequest actualAssetRequest = assetRequestCaptor.getValue();
        assertThat(actualAssetRequest.getDownloadFolder()).isEqualTo(assetDestFilePath);
        assertThat(actualAssetRequest.getDownloadUri()).isEqualTo(assetDownloadURI);
        assertThat(actualAssetRequest.getSubsetting()).isNull();
        assertThat(actualAssetRequest.getUserUuid()).isEqualTo(USER_UUID);

        inOrder.verify(mockObjectMapper).writeValueAsBytes(stacItem);
        inOrder.verify(mockObjectMapper).writeValueAsString(stacCatalog);


    }

    @Test
    public void testStageInInput_CreatesDownloadRequestWithoutUserUuid_WhenUserUuidIsNull() throws Exception {

        Files.createDirectories(stacDocumentTempFolder);
        assertThat(Files.exists(stacDocumentTempFolder)).isTrue();

        Path stageInDir = Paths.get(testPath.toString(), "stageInDir");
        Path assetsDir = Paths.get(testPath.toString(), "assetsDir");
        Multimap<String, String> inputs = ArrayListMultimap.create();
        String fileInputKey = "inputKey";
        inputs.put(fileInputKey, "example.com");

        URI downloadURI = new URI("example.com");
        Path destFilePath = Paths.get(stacDocumentTempFolder.toString(), fileInputKey);


        when(mockDownloaderFacade.download(eq(new DownloadRequest(downloadURI, destFilePath, null, null))))
                .thenReturn(Files.createDirectories(Paths.get(testPath.toString(),"expectedDownloadPath")));
        stageInService.stageInInputs(stageInDir, assetsDir, inputs, null);

        ArgumentCaptor<DownloadRequest> requestCaptor = ArgumentCaptor.forClass(DownloadRequest.class);
        inOrder.verify(mockDownloaderFacade).download(requestCaptor.capture());
        DownloadRequest actualRequest = requestCaptor.getValue();
        assertThat(actualRequest.getDownloadFolder()).isEqualTo(destFilePath);
        assertThat(actualRequest.getDownloadUri()).isEqualTo(downloadURI);
        assertThat(actualRequest.getSubsetting()).isNull();
        assertThat(actualRequest.getUserUuid()).isNull();
    }

    private void cleanTestFiles() {
        if(Files.exists(baseTestDir)) {
            try {
                cleanDirectory(baseTestDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void cleanDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}