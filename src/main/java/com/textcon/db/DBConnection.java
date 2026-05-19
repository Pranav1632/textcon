package com.textcon.db;

import com.textcon.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS conversion_history (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              original_text TEXT,
              converted_text TEXT,
              conversion_type VARCHAR(50),
              export_path TEXT,
              export_format VARCHAR(20),
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

    private static DBConnection instance;

    private final String jdbcUrl;

    private DBConnection(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initializeDatabase();
    }

    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection("jdbc:sqlite:" + resolveDbPath());
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    private void initializeDatabase() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE_SQL);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize SQLite database", ex);
        }
    }

    private static Path resolveDbPath() {
        String configuredPath = System.getProperty("textcon.db.path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path dbPath = Path.of(configuredPath).toAbsolutePath().normalize();
            tryCreateParentDirectory(dbPath);
            return dbPath;
        }

        try {
            return AppPaths.resolveDatabasePath().toAbsolutePath().normalize();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare application data directory", ex);
        }
    }

    private static void tryCreateParentDirectory(Path dbPath) {
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create database directory: " + parent, ex);
        }
    }
}
