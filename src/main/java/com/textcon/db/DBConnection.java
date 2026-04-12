package com.textcon.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {
    private static final String DEFAULT_DB_FILE = "textcon.db";
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
            String dbPath = System.getProperty("textcon.db.path", DEFAULT_DB_FILE);
            instance = new DBConnection("jdbc:sqlite:" + dbPath);
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
}
