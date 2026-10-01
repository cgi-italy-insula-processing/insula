package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@TestConfiguration
@Import(InputDownloaderCoreConfig.class)
public class InputDownloaderCoreTestConfig {

    @Bean
    public FileSystem fileSystem() {
        return Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix", "unix").build());
    }

    @Bean
    public Path basePath(FileSystem fileSystem, @Value("${base_path:/baseDir}") String basePath) throws IOException {
        return Files.createDirectories(fileSystem.getPath(basePath).toAbsolutePath());
    }

    @Bean
    public Path stacDocumentTempFolder(FileSystem fileSystem, @Value("${platform.inputdownloader.stacDocumentTempFolder:/data/tempDir}") String stacDocumentTempFolder) throws IOException {
        return Files.createDirectories(fileSystem.getPath(stacDocumentTempFolder).toAbsolutePath());
    }
}
