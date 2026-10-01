package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service class responsible for preparing the environment required for job execution.
 */

@Log4j2
@AllArgsConstructor
public class EnvironmentService {

    public static final String INPUT_DIR = "inDir";
    public static final String OUTPUT_DIR = "outDir";
    public static final String PERSISTENT_DIR = "persistent";
    public static final String STAGE_IN_DIR = "stageIn";
    public static final List<String> LEGACY_JOB_CONFIG_FILENAMES = ImmutableList.of("FSTEP-WPS-INPUT.properties");
    public static final String JOB_CONFIG_FILENAME = "WPS-INPUT.properties";
    public static final String JOB_INPUTS_JSON_FILENAME = "jobInputs.json";

    private static final String OUTPUT_DIR_PERMISSIONS = "rwxrwxr-x";

    private final StageInService stageInService;
    private final DownloaderService downloaderService;
    private final K8sJobParams k8sJobParams;
    private final ObjectMapper mapper;

    /**
     * Prepares the environment for a given job by creating necessary directories and downloading inputs.
     * It also generates job configuration files based on the inputs retrieved from the platform.
     *
     * @param jobId    The unique identifier for the job.
     * @param basePath The base path where the environment directories will be created.
     * @param userUuid    the userUuid of the job owner
     * @throws IOException if there is an issue creating directories or writing files.
     */
    public void prepareEnvironment(String jobId, Path basePath, String userUuid) throws IOException {
        Path inputDir = basePath.resolve(INPUT_DIR);
        Path outputDir = basePath.resolve(OUTPUT_DIR);

        createDirectory(inputDir);
        createDirectory(basePath.resolve(PERSISTENT_DIR));
        createDirectory(outputDir);
        Files.setPosixFilePermissions(outputDir, PosixFilePermissions.fromString(OUTPUT_DIR_PERMISSIONS));

        createJobConfigFile(jobId, basePath, k8sJobParams.getParams());
        createJobInputsJsonFile(jobId, basePath, k8sJobParams.getParams());

        stageInStacInputs(basePath, inputDir, userUuid);
        downloaderService.downloadInputs(inputDir, k8sJobParams.getDownloadableParams(), userUuid);
    }

    private void stageInStacInputs(Path basePath, Path inputDir, String userUuid) throws IOException {
        if (k8sJobParams.getStacParams().isEmpty()) {
            return;
        }

        Path stageInDir = basePath.resolve(STAGE_IN_DIR);
        createDirectory(stageInDir);
        stageInService.stageInInputs(stageInDir, inputDir, k8sJobParams.getStacParams(), userUuid);
    }

    private static void createDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }
    }

    private void createJobConfigFile(String jobId, Path workingDir, Multimap<String, String> inputs) throws IOException {
        if (!inputs.isEmpty()) {
            Path configFile = workingDir.resolve(JOB_CONFIG_FILENAME);
            createConfigFile(inputs, configFile);

            LOG.info("Created job configuration file for job {} in location: {}", jobId, configFile);
            for (String legacyJobConfigFileName : LEGACY_JOB_CONFIG_FILENAMES) {
                Path legacyConfigFile = workingDir.resolve(legacyJobConfigFileName);
                createConfigFile(inputs, legacyConfigFile);
                LOG.info("Created legacy job configuration file for job {} in location: {}", jobId, legacyConfigFile);
            }
        }
    }

    private void createConfigFile(Multimap<String, String> inputs, Path configFile) throws IOException {
        List<String> configFileLines = inputs.keySet().stream()
                .map(key -> key + "=" + wrapWithQuote(String.join(",", inputs.get(key))))
                .collect(Collectors.toList());
        Files.write(configFile, configFileLines, StandardOpenOption.CREATE);
    }

    private void createJobInputsJsonFile(String jobId, Path workingDir, Multimap<String, String> inputs) throws IOException {

        if (inputs.isEmpty()) {
            LOG.info("Job inputs are empty for job {}. Skipping creation of job inputs JSON file.", jobId);
            return;
        }

        Path jsonFile = workingDir.resolve(JOB_INPUTS_JSON_FILENAME);

        try (OutputStream os = Files.newOutputStream(jsonFile)) {
            mapper.writeValue(os, buildMapFromMultimap(inputs));
        }

        LOG.info("Created job inputs JSON file for job {} in location: {}", jobId, jsonFile);
    }

    private Map<String, Object> buildMapFromMultimap(Multimap<String, String> source) {
        return source.asMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ArrayList<>(entry.getValue()))
                );
    }

    private static String wrapWithQuote(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        char wrapWith = '"';
        boolean wrapStart = str.charAt(0) != wrapWith;
        boolean wrapEnd = str.charAt(str.length() - 1) != wrapWith;
        if (!wrapStart && !wrapEnd) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length() + 2);
        if (wrapStart) {
            sb.append(wrapWith);
        }
        sb.append(str);
        if (wrapEnd) {
            sb.append(wrapWith);
        }
        return sb.toString();
    }
}