package com.cgi.eoss.platform.core.processing.io.download;


import com.cgi.eoss.platform.core.processing.io.ServiceIoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Multi-protocol downloader facade
 * */
@Log4j2
@RequiredArgsConstructor
public class SimpleDownloaderFacade implements DownloaderFacade {

    private final Set<Downloader> downloaders;

    @Override
    public Path download(DownloadRequest downloadRequest) {
        URI uri = downloadRequest.getDownloadUri();
        Path target = downloadRequest.getDownloadFolder();
        createDirectories(target);

        for (Downloader downloader : getAvailableDownloaders(uri)) {
            Optional<Path> downloadedInput = attemptDownload(downloader, downloadRequest);
            if (downloadedInput.isPresent()) {
                return downloadedInput.get();
            }
        }

        deleteQuietly(target);
        throw new ServiceIoException("No downloader was able to process the URI: " + uri);
    }

    @Override
    public Map<URI, Path> download(List<DownloadRequest> downloadRequests) {
        return downloadRequests.stream()
                .collect(Collectors.toMap(DownloadRequest::getDownloadUri, this::download));
    }

    @Override
    public boolean isSupportedProtocol(String scheme) {
        return downloaders.stream()
                .anyMatch(d -> d.getProtocols().contains(scheme));
    }

    @Override
    public void cleanUp(URI uri) {
        throw new UnsupportedOperationException(String.format("Cannot clean up '%s', operation not implemented", uri));
    }

    private List<Downloader> getAvailableDownloaders(URI downloadUri) {
        return downloaders.stream()
                .filter(d -> d.getProtocols().contains(downloadUri.getScheme()))
                .collect(Collectors.toList());
    }

    private static Optional<Path> attemptDownload(Downloader downloader, DownloadRequest downloadRequest) {
        Path target = downloadRequest.getDownloadFolder();
        Path tempDir = createTempDirectoryIn(target);
        DownloadRequest tempRequest = new DownloadRequest(
                downloadRequest.getDownloadUri(), tempDir, downloadRequest.getSubsetting());

        try {
            return downloadToTempDir(downloader, tempRequest)
                    .map(downloadedFile -> moveToTarget(downloadedFile, target));
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private static Optional<Path> downloadToTempDir(Downloader downloader, DownloadRequest tempRequest) {
        try {
            return Optional.of(downloader.download(tempRequest));
        } catch (Exception e) {
            LOG.warn("Cannot download from URI '{}' using '{}'",
                    tempRequest.getDownloadUri(), downloader, e);
            return Optional.empty();
        }
    }

    private static Path moveToTarget(Path source, Path target) {
        try {
            return Files.move(source, target.resolve(source.getFileName()),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceIoException("Move of downloaded input from " + source + " to " + target + " failed", e);
        }
    }

    private static Path createTempDirectoryIn(Path parent) {
        try {
            return Files.createTempDirectory(parent, "temp-");
        } catch (IOException e) {
            throw new ServiceIoException("Cannot create temp directory in: " + parent, e);
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new ServiceIoException("Cannot create download folder: " + directory, e);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(SimpleDownloaderFacade::deleteIfExist);
        } catch (IOException e) {
            LOG.debug("Cannot delete temporary download folder: {}", e.getMessage());
        }
    }

    private static void deleteIfExist(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.error("Cannot delete '{}': '{}'", path, e.getMessage());
        }
    }
}
