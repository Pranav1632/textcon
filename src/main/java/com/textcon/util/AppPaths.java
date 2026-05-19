package com.textcon.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AppPaths {
    private static final String APP_DIR_NAME = "TextCon";
    private static final String DB_FILE_NAME = "textcon.db";

    private AppPaths() {
    }

    public static Path resolveDatabasePath() throws IOException {
        Path appDataDirectory = resolveAppDataDirectory();
        Path databasePath = appDataDirectory.resolve(DB_FILE_NAME);
        migrateLegacyDatabaseIfNeeded(databasePath);
        return databasePath;
    }

    public static Path resolveAppDataDirectory() throws IOException {
        String override = System.getProperty("textcon.app.data");
        Path appDataDirectory;
        if (override != null && !override.isBlank()) {
            appDataDirectory = Path.of(override);
        } else {
            appDataDirectory = windowsDataRoot().resolve(APP_DIR_NAME);
        }
        Files.createDirectories(appDataDirectory);
        return appDataDirectory;
    }

    private static Path windowsDataRoot() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("win")) {
            throw new IllegalStateException("TextCon currently supports Windows 10/11 only.");
        }

        String userHome = System.getProperty("user.home");
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData);
        }
        return Path.of(userHome, "AppData", "Roaming");
    }

    private static void migrateLegacyDatabaseIfNeeded(Path targetDatabasePath) throws IOException {
        if (Files.exists(targetDatabasePath)) {
            return;
        }

        Path legacyPath = Path.of(System.getProperty("user.dir"), DB_FILE_NAME);
        if (!Files.exists(legacyPath)) {
            return;
        }
        if (legacyPath.toAbsolutePath().normalize().equals(targetDatabasePath.toAbsolutePath().normalize())) {
            return;
        }

        Files.copy(legacyPath, targetDatabasePath, StandardCopyOption.COPY_ATTRIBUTES);
    }
}
