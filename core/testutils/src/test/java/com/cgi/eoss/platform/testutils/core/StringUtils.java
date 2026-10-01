package com.cgi.eoss.platform.testutils.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cgi.eoss.platform.testutils.core.constants.CommonPaths;

public final class StringUtils {

    private StringUtils() {
    }

    public static String readResourceAsString(Path path) {
        return readAsString(CommonPaths.BASE_TEST_PATH.resolve(path));
    }

    public static String readAsString(Path path) {
        try {
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String withoutNewLines(String value) {
        return value.replaceAll("\r", "").replaceAll("\n", "");
    }
}
