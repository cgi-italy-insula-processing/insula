package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.download.DownloadRequest;
import com.cgi.eoss.platform.core.processing.io.download.DownloaderFacade;
import com.cgi.eoss.platform.core.processing.io.download.Subsetting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import org.apache.commons.io.FilenameUtils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Class responsible to download job inputs
 */

@AllArgsConstructor
public class DownloaderService {

    private final DownloaderFacade downloaderFacade;
    private final boolean createSubdirectories;

    /**
     * Download the downloadable job inputs within the provided base input folder
     * @param inputDir base folder for the input(s) download
     * @param downloadableParams a list with the downloadable job inputs
     * @param userUuid identifier of the user requesting the download
     * @return a map containing, for all the downloadable inputs, the path to the downloaded files
     * @throws java.io.UncheckedIOException in case of IO error during the download
     */
    public Multimap<String, Path> downloadInputs(Path inputDir, List<K8sJobParams.DownloadableParam> downloadableParams, String userUuid) {
        Multimap<String, Path> downloadedJobInputs = ArrayListMultimap.create();
        for (K8sJobParams.DownloadableParam downloadableParam : downloadableParams) {
            Set<URI> inputUris = getDownloadableParamValidUris(downloadableParam.getValues());
            Path subdirPath = inputDir.resolve(downloadableParam.getParamName());
            downloadedJobInputs.putAll(downloadableParam.getParamName(), prepareInput(subdirPath, inputUris, downloadableParam.getSubsetting(), userUuid));
        }
        return downloadedJobInputs;
    }

    private Set<URI> getDownloadableParamValidUris(List<String> values) {
        return values.stream()
                .filter(this::isValidUri)
                .map(URI::create)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Path> prepareInput(Path target, Collection<URI> uris, Subsetting subsetting, String userUuid) {
        List<DownloadRequest> requests = isSubdirectoriesCreationRequired(uris)
                ? mapUrisToUniquePaths(uris, target).entrySet()
                        .stream()
                        .map(entry -> new DownloadRequest(entry.getKey(), entry.getValue(), subsetting, userUuid))
                        .collect(Collectors.toList())
                : uris.stream()
                        .map(uri -> new DownloadRequest(uri, target, subsetting, userUuid))
                        .collect(Collectors.toList());

        return new ArrayList<>(downloaderFacade.download(requests).values());
    }

    private boolean isSubdirectoriesCreationRequired(Collection<URI> uris) {
        return createSubdirectories && uris.size() != 1;
    }

    private boolean isValidUri(String test) {
        try {
            URI uri = URI.create(test);
            return uri.getScheme() != null && downloaderFacade.isSupportedProtocol(uri.getScheme());
        } catch (Exception unused) {
            return false;
        }
    }

    private static Map<URI, Path> mapUrisToUniquePaths(Iterable<URI> uris, Path targetBaseDir) {
        Map<URI, Path> result = new LinkedHashMap<>();
        Map<String, Integer> nameCounters = new LinkedHashMap<>();

        for (URI uri : uris) {
            String baseName = getDownloadFolderName(uri);

            int index = nameCounters.getOrDefault(baseName, 0);
            nameCounters.put(baseName, index + 1);

            String finalName = index == 0
                    ? baseName
                    : createNameWithIndexSuffix(baseName, index);

            result.put(uri, targetBaseDir.resolve(finalName));
        }
        return result;
    }

    private static String getDownloadFolderName(URI uri) {
        String fileName = Paths.get(uri.getPath()).getFileName().toString();
        return isZipFile(uri) ? FilenameUtils.removeExtension(fileName) : fileName;
    }

    private static String createNameWithIndexSuffix(String baseName, int index) {
        String fileExtension = FilenameUtils.getExtension(baseName);
        return Objects.isNull(fileExtension) || fileExtension.isEmpty()
                ? baseName + "_" + index
                : FilenameUtils.removeExtension(baseName) + "_" + index + "." + fileExtension;

    }

    private static boolean isZipFile(URI uri) {
        String fileName = Paths.get(uri.getPath()).getFileName().toString();
        return "zip".equals(FilenameUtils.getExtension(fileName));
    }
}
