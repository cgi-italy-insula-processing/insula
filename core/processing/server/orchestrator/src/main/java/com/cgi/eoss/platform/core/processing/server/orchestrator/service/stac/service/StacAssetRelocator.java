package com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.service;

import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.Asset;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument.StacItem;
import com.google.common.net.UrlEscapers;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible to relocate the assets of STAC Items referencing files through relative paths to the URLs
 * the files can be downloaded from.
 */
@Log4j2
public class StacAssetRelocator {

    /**
     * Placeholder representing the percent-encoded file path in the file download URL templates
     */
    public static final String FILE_NAME_PLACEHOLDER = "{filename}";

    /**
     * Relocates the assets of the provided STAC Items: the href of every asset referencing a file through a relative
     * path is replaced with the URL obtained by replacing {@link #FILE_NAME_PLACEHOLDER} within the provided template
     * with the percent-encoded relative path of the file.
     *
     * @param items The STAC Items whose assets should be relocated
     * @param fileDownloadUrlTemplate The template of the URL the referenced files can be downloaded from, holding
     *                                {@link #FILE_NAME_PLACEHOLDER} where the file path is expected
     */
    public void relocateAssets(List<StacItem> items, String fileDownloadUrlTemplate) {
        if (!fileDownloadUrlTemplate.contains(FILE_NAME_PLACEHOLDER)) {
            throw new IllegalArgumentException("File download URL template " + fileDownloadUrlTemplate
                    + " does not hold the file name placeholder " + FILE_NAME_PLACEHOLDER);
        }
        for (StacItem item : items) {
            relocateAssets(item, fileDownloadUrlTemplate);
        }
    }

    private static void relocateAssets(StacItem item, String fileDownloadUrlTemplate) {
        if (item.getAssets() == null) {
            return;
        }
        for (Asset asset : item.getAssets().values()) {
            relocateAsset(item.getId(), asset, fileDownloadUrlTemplate);
        }
    }

    private static void relocateAsset(String itemId, Asset asset, String fileDownloadUrlTemplate) {
        Optional<String> relativeFilePath = relativeFilePath(asset.getHref());
        if (!relativeFilePath.isPresent()) {
            LOG.warn("Leaving untouched the href {} of an asset of item {} since it is not a relative file path",
                    asset.getHref(), itemId);
            return;
        }

        URI href = URI.create(fileDownloadUrlTemplate.replace(FILE_NAME_PLACEHOLDER, encodeForUrl(relativeFilePath.get())));
        LOG.debug("Relocating the href {} of an asset of item {} to {}", asset.getHref(), itemId, href);
        asset.setHref(href);
    }

    private static Optional<String> relativeFilePath(URI href) {
        if (href == null) {
            return Optional.empty();
        }
        return relativeUnixPath(href.normalize())
                .filter(path -> isValidFileName(fileName(path)));
    }

    private static Optional<String> relativeUnixPath(URI href) {
        if (href.getScheme() != null || href.getRawAuthority() != null) {
            return Optional.empty();
        }
        String rawPath = href.getRawPath();
        if (rawPath.isEmpty() || rawPath.startsWith("/")) {
            return Optional.empty();
        }
        return Optional.of(href.getPath());
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static boolean isValidFileName(String filename) {
        return !filename.isEmpty() && !filename.equals(".") && !filename.equals("..");
    }

    private static String encodeForUrl(String filePath) {
        return UrlEscapers.urlFormParameterEscaper().escape(filePath).replace("+", "%20");
    }

}
