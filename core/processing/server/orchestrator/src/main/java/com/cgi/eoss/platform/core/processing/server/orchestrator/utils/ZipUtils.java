package com.cgi.eoss.platform.core.processing.server.orchestrator.utils;

import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class to handle zip files.
 */
@UtilityClass
public class ZipUtils {

    /**
     * Unzip data coming from a stream source into a destination folder.
     *
     * @param zipFileInputStream input stream for the archive zip file
     * @param folder destination folder, it will be created if not existent
     * @return the path to the destination folder containing unzipped files.
     * @throws IOException in case of error related to input/output streams.
     */
    public static Path unzipInFolder(InputStream zipFileInputStream, Path folder) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(zipFileInputStream)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(folder.toFile(), zipEntry);
                if (zipEntry.isDirectory()) {
                    createDir(newFile);
                } else {
                    // Check if the entry is inside a folder and eventually create it
                    File parent = newFile.getParentFile();
                    createDir(parent);
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
        return folder;
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static void createDir(File newFile) throws IOException {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
                throw new IOException("Failed to create directory " + newFile);
        }
    }
}
