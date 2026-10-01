package com.cgi.eoss.platform.testutils.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Convenience class that contains methods to work with Files
 *
 * @author cantaveneraf
 *
 */
public final class FilesUtils {

    private FilesUtils() {
    }

    /**
     * List the content of a directory
     *
     * @param dir
     *            The directory to list
     * @return
     *         The list of paths representing the directory content
     */
    public static List<Path> list(Path dir) {
        List<Path> dirContent;
        try (Stream<Path> stream = Files.list(dir)) {
            dirContent = stream.collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dirContent;
    }

    /**
     * List the content of a directory
     *
     * @param dir
     *            The directory to list
     * @param options
     *            The file tree traversal options
     *
     * @return
     *         The list of paths representing the directory content
     */

    public static List<Path> walk(Path dir,  FileVisitOption... options) {
        List<Path> dirContent;
        try (Stream<Path> stream = Files.walk(dir,options)) {
            dirContent = stream.collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dirContent;
    }

    /**
     *  Recursively delete the content of the provided directory, if it exists
     *
     * @param dir
     *          The directory to delete
     */
    public static void deleteDirContentsIfExists(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        deleteDirContents(dir);
    }

    private static void deleteDirContents(Path dir) {
        for(Path path: list(dir)) {
            if(Files.isDirectory(path)) {
                deleteDirContents(path);
            }
            deleteUnchecked(path);
        }
    }

    private static void deleteUnchecked(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Unzip a file in the provided target dir.
     * @param zipFile path to the zip file
     * @param targetDir path to the destination folder for the unzipped files
     * @return the directory where the file were unzipped
     * @throws IOException if there are problems while unzipping
     */
    public static Path unzipFile(Path zipFile, Path targetDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry()) {
                Path newFile = newFile(targetDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (Files.isDirectory(newFile)) {
                        Files.createDirectories(newFile);
                    }
                } else {
                    writeFile(newFile, zis, buffer);
                }
                zis.closeEntry();
            }
        }
        return targetDir;
    }

    /**
     * Reads all the files contained in the specified folder into a map.
     *
     * <p>Can be used in conjunction with
     * {@link com.cgi.eoss.platform.testutils.core.FilesUtils#readZipEntries(byte[])} for comparison/testing purposes.
     *
     * <p>For each file adds an entry to the map:
     *
     * <pre>{@code
     * Key: filename (String)
     * Value: content of the file with \n line feeds (String)
     * }</pre>
     *
     * @param folder folder containing the files to read
     * @return a map containing a representation of the folder's content
     */
    public static Map<String, String> readFolderFiles(Path folder) {
        return FilesUtils.list(folder).stream()
                .collect(Collectors.toMap(p -> p.getFileName().toString(), p -> readAsString(p).replace("\r\n", "\n")));
    }

    /**
     * Reads all the file entries contained in the specified zipfile into a map.
     *
     * <p>Can be used in conjunction with
     * {@link com.cgi.eoss.platform.testutils.core.FilesUtils#readFolderFiles(Path)} for comparison/testing purposes.
     *
     * <p>For each file inside the zipfile adds an entry to the map:
     *
     * <pre>{@code
     * Key: filename (String)
     * Value: content of the file with \n line feeds (String)
     * }</pre>
     *
     * @param zipContent zipfile containing the entries to read
     * @return  a map containing a representation of the zipfile's content
     */
    public static Map<String, String> readZipEntries(byte[] zipContent) {
        Map<String, String> zipEntries = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipContent))) {

            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry()) {
                String entryName = zipEntry.getName();
                zipEntries.put(entryName, dropComments(entryName, readAsString(zis)));
                zis.closeEntry();
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return zipEntries;
    }

    private static void writeFile(Path newFile, ZipInputStream zis, byte[] buffer) throws IOException {
        try (OutputStream fos = Files.newOutputStream(newFile)) {
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    private static Path newFile(Path destinationDir, ZipEntry zipEntry) throws IOException {
        Path destFile = destinationDir.resolve(zipEntry.getName()).normalize();

        if (!destFile.startsWith(destinationDir)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static String dropComments(String fileName, String fileContent) {
        if (fileName.endsWith(".properties")) {
            fileContent = fileContent.replaceAll("#.*\n?", "");
        }
        return fileContent;
    }

    private static String readAsString(Path path) {
        try {
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readAsString(InputStream is) {
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }
}
