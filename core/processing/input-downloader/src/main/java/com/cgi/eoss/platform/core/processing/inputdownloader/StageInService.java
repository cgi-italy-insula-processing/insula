package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacCatalog;
import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacDocument;
import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacDocument.*;
import com.cgi.eoss.platform.core.processing.inputdownloader.stac.model.StacLink;
import com.cgi.eoss.platform.core.processing.io.download.DownloaderFacade;
import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * This class is responsible for the download and organization of files involved in the stage in phase.
 */
@Log4j2
@AllArgsConstructor
public class StageInService {

    private static final String PROCESS_ALL_ITEMS = "*";

    private final DownloaderFacade doNotUnzipDownloaderFacade;

    private final ObjectMapper objectMapper;

    private final Path stacDocumentTempFolder;

    /**
     * Perform stage in operations for provided inputs.
     *
     * @param stageInDir the path to the stage in folder, will contain catalog files for provided inputs.
     * @param assetDir   the path to the root folder that will contain assets.
     * @param inputs     inputs for which the stage in phase is required.
     * @param userUuid The userUuid of the job owner
     * @throws IOException if files related operations fail.
     */
    public void stageInInputs(Path stageInDir, Path assetDir, Multimap<String, String> inputs, String userUuid)
            throws IOException {
        if (!Files.exists(stacDocumentTempFolder)) {
            Files.createDirectory(stacDocumentTempFolder);
        }

        HashMap<String, String> inputIdToStacItemId = new HashMap<>();
        Multimap<String, String> inputIdToStacDocumentURLs =
                prepareStageInInputsDocumentURLs(inputs, inputIdToStacItemId);
        Multimap<String, Path> downloadedStacDocuments =
                downloadFiles(stacDocumentTempFolder, inputIdToStacDocumentURLs, userUuid);

        for (String inputId : downloadedStacDocuments.keySet()) {
            Path stageInInputDir = Files.createDirectories(stageInDir.resolve(inputId));
            for (Path stacDocumentPath : downloadedStacDocuments.get(inputId)) {
                StacDocument parsedDocument = parseStacDocument(stacDocumentPath, inputIdToStacItemId.get(inputId));
                processStacDocument(assetDir, inputId, parsedDocument, stageInInputDir, userUuid);
                LOG.info("Document {} for input {} processed", stacDocumentPath, inputId);
            }
        }
    }

    private static Multimap<String, String> prepareStageInInputsDocumentURLs(Multimap<String, String> inputs,
                                                                             Map<String, String> inputIdToStacItem) {
        Multimap<String, String> inputIdToStacDocumentURLs = ArrayListMultimap.create();
        for (Map.Entry<String, String> entry : inputs.entries()) {
            String documentUrl = entry.getValue();
            String itemIdToDownload = PROCESS_ALL_ITEMS;
            URI valueAsUri = URI.create(documentUrl);
            String fragment = valueAsUri.getRawFragment();
            if (fragment != null) {
                itemIdToDownload = fragment;
                documentUrl = documentUrl.replace("#".concat(fragment), "");
            }
            String inputId = entry.getKey();
            inputIdToStacItem.put(inputId, itemIdToDownload);
            inputIdToStacDocumentURLs.put(inputId, documentUrl);
        }
        return inputIdToStacDocumentURLs;
    }

    private void processStacDocument(Path assetDir, String inputId, StacDocument stacDocument, Path stageInInputDir, String userUuid) throws IOException {
        // Root folder for all the assets related to the input processed
        Path documentAssetsDir = assetDir.resolve(inputId);

        if (!Files.exists(documentAssetsDir)) {
            Files.createDirectories(documentAssetsDir);
        }

        LOG.info("Processing document for input {}", inputId);
        Map<String, Path> itemsInDocument =
                processStacItems(documentAssetsDir, stacDocument.getItems(), stageInInputDir, userUuid);
        List<StacLink> stacItemsLinks = createStacLinks(itemsInDocument);
        StacCatalog stacCatalog = prepareCatalogue(inputId, stacItemsLinks);
        writeCatalogJson(stacCatalog, stageInInputDir);
    }

    private Map<String, Path> processStacItems(Path assetsDirBasePath, List<StacItem> stacItems, Path stageInInputDir,
                                               String userUuid) throws IOException {
        Map<String, Path> stacItemsPaths = new HashMap<>();
        for (StacItem item : stacItems) {
            Path itemAssetsDir = assetsDirBasePath.resolve(item.getId());

            if (!Files.exists(itemAssetsDir)) {
                Files.createDirectories(itemAssetsDir);
            }

            LOG.info("Downloading assets for item: {}", item.getId());
            Map<String, Path> downloadedAssets = downloadItemAssets(item.getAssets(), itemAssetsDir, userUuid);
            rewriteItemAssetsLinks(item, downloadedAssets);

            LOG.info("Assets downloaded, creating item JSON file for {}", item.getId());
            stacItemsPaths.put(item.getId(), writeStacItemJson(item, stageInInputDir));
        }
        return stacItemsPaths;
    }

    private void rewriteItemAssetsLinks(StacItem item, Map<String, Path> downloadedAssets) {
        downloadedAssets.forEach((assetName, assetPath) -> {
            item.getAssets().get(assetName).setHref(assetPath.toAbsolutePath().toString());
        });
    }

    private Map<String, Path> downloadItemAssets(Map<String, Asset> assets, Path assetDir, String userUuid)
            throws IOException {

        Multimap<String, String> assetIdsToURLs = HashMultimap.create();

        assets.forEach((assetName, assetProperties) -> assetIdsToURLs.put(assetName, assetProperties.getHref()));

        Multimap<String, Path> assetDownloaded = downloadFiles(assetDir, assetIdsToURLs, userUuid);

        return flatAssetMultimap(assetDownloaded);
    }

    private Path writeStacItemJson(StacItem item, Path targetFolder) throws IOException {
        Path itemJsonPath = targetFolder.resolve(item.getId()  + ".json");
        Files.write(itemJsonPath, objectMapper.writeValueAsBytes(item));
        return itemJsonPath;
    }

    private void writeCatalogJson(StacCatalog catalog, Path targetFolder) throws IOException {
        Path catalogPath = targetFolder.resolve("catalog.json");
        String parsedCatalog = objectMapper.writeValueAsString(catalog);
        Files.write(catalogPath, parsedCatalog.getBytes());
    }

    private StacDocument parseStacDocument(Path stacDocumentPath, String itemIdToProcess) throws IOException {
        LOG.info("Parsing document at {} for item {}", stacDocumentPath, itemIdToProcess);
        StacDocument stacDocument = objectMapper.readValue(Files.readAllBytes(stacDocumentPath), StacDocument.class);
        if (!PROCESS_ALL_ITEMS.equals(itemIdToProcess)) {
            populateWithTargetItem(stacDocument, itemIdToProcess);
        }
        return stacDocument;
    }

    private Multimap<String, Path> downloadFiles(Path targetDir, Multimap<String, String> fileToDownload, String userUuid)
            throws IOException {
        Multimap<String, Path> downloadedFiles = HashMultimap.create();
        Map<String, URI> flattedMultimap = flatMultimap(fileToDownload);
        // Assumption made: each value contains only one URI, enforced by flatMultimap method
        for (String file : flattedMultimap.keySet()) {
            // This line prevents the downloader facade to throw a "directory not empty" exception when moving downloaded file
            // to the target dir
            Path destBasePath = targetDir.resolve(file);
            Path downloadPath = doNotUnzipDownloaderFacade.download(
                    new DownloadRequest(flattedMultimap.get(file), destBasePath, null, userUuid));
            try(Stream<Path> targetPath = Files.walk(downloadPath)) {
                targetPath.filter(Files::isRegularFile)
                        .findFirst()
                        .ifPresent(downloadedFile -> downloadedFiles.put(file, downloadedFile));
            }
        }
        return downloadedFiles;
    }

    private static Map<String, Path> flatAssetMultimap(Multimap<String, Path> assets) {
        Map<String, Path> downloadedAssets = new HashMap<>();
        for (String assetName : assets.keySet()) {
            for (Path asset : assets.get(assetName)) {
                downloadedAssets.put(assetName, asset);
            }
        }
        return downloadedAssets;
    }

    private static void populateWithTargetItem(StacDocument stacDocument, String itemId) {
        stacDocument.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .ifPresent(stacItem -> stacDocument.setItems(Collections.singletonList(stacItem)));
    }

    private static List<StacLink> createStacLinks(Map<String, Path> items) {
        return items.entrySet().stream().map((item) -> StacLink.builder()
                .type("application/geo+json")
                .rel("item")
                .title(item.getKey())
                .href(item.getValue().toAbsolutePath().toString())
                .build()).collect(Collectors.toList());
    }

    private static StacCatalog prepareCatalogue(String inputId, List<StacLink> stacLinks) {
        return StacCatalog.builder()
                .id(inputId)
                .type("Catalog")
                .stacVersion("1.0.0")
                .description("Root catalog for input " + inputId)
                .links(stacLinks)
                .build();
    }

    private static Map<String, URI> flatMultimap(Multimap<String, String> multiMapToFlat) {
        Map<String, URI> flattedMap = new HashMap<>();
        for (String key: multiMapToFlat.keySet()) {
            flattedMap.put(key, URI.create(multiMapToFlat.get(key).iterator().next()));
        }
        return flattedMap;
    }
}
