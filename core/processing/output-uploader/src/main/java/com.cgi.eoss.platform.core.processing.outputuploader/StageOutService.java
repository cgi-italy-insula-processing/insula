package com.cgi.eoss.platform.core.processing.outputuploader;


import com.cgi.eoss.platform.core.processing.outputuploader.stac.StacCatalog;
import com.cgi.eoss.platform.core.processing.outputuploader.stac.StacDocument;
import com.cgi.eoss.platform.core.processing.outputuploader.stac.StacDocument.StacItem;
import com.cgi.eoss.platform.core.processing.outputuploader.stac.StacLink;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This service is responsible for the stage out phase which consist in creating an
 * archive  containing assets and metadata starting from a STAC catalog
 */
@Slf4j
@RequiredArgsConstructor
public class StageOutService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final long fileSizeByteThreshold;

    /**
     * Creates an archive for each of the STAC Items contained within the STAC catalog provided in input.
     * The archive will contain the assets and the StacItem json file(s).
     * @param stacCatalogPath path to the stac catalog holding references to the output items
     * @param destinationFolder the path in which the generated archives will be stored
     * @return a list of path pointing to the (one or more) generated archive(s).
     * @throws IOException in case of issue during catalog parse or archive creation.
     */
    public List<Path> archiveStacCatalog(Path stacCatalogPath, Path destinationFolder) throws IOException {
        StacCatalog stacCatalog = parseStacCatalog(stacCatalogPath);
        List<Path> archivedStacItems = new ArrayList<>();
        for (StacLink link : stacCatalog.getLinks()) {
            if ("root".equals(link.getRel()) || "self".equals(link.getRel())) {
                continue;
            }
            Path itemPath = resolve(stacCatalogPath.getParent(), Paths.get(link.getHref()));
            archivedStacItems.add(archiveStacItem(itemPath, destinationFolder));
        }
        return archivedStacItems;
    }

    private Path archiveStacItem(Path stacItemPath, Path destinationFolder) throws IOException {
        StacItem stacItem = parseStacItem(stacItemPath);

        Path archivePath = destinationFolder.resolve(stacItem.getId() + ".zip");
        try (FileOutputStream fos = new FileOutputStream(archivePath.toString()); ZipOutputStream archive = new ZipOutputStream(fos)) {

                for (Map.Entry<String, StacDocument.Asset> asset : stacItem.getAssets().entrySet()) {
                    addToArchive(resolve(stacItemPath.getParent(), asset.getValue().getHref()), archive);
                }
                addToArchive(stacItemPath, archive);
        }

        return archivePath;
    }

    private static void addToArchive(Path file,  ZipOutputStream archive) throws IOException {
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            archive.putNextEntry(new ZipEntry(file.getFileName().toString()));

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                archive.write(buffer, 0, length);
            }

            archive.closeEntry();
        }
    }

    private static Path resolve(Path basePath, Path targetPath) {
        if (targetPath.isAbsolute()) {
            return targetPath;
        } else {
            return basePath.resolve(targetPath);
        }
    }

    private StacCatalog parseStacCatalog(Path catalogPath) throws IOException {
        checkFileSize(catalogPath);
        return OBJECT_MAPPER.readValue(Files.readAllBytes(catalogPath), StacCatalog.class);
    }

    private StacItem parseStacItem(Path stacItemPath) throws IOException {
        checkFileSize(stacItemPath);
        return OBJECT_MAPPER.readValue(Files.readAllBytes(stacItemPath), StacItem.class);
    }

    private void checkFileSize(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file)) {
            if (channel.size() > fileSizeByteThreshold) {
                throw new IllegalArgumentException("File " + file + " is too large");
            }
        }
    }
}
