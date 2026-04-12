package com.textcon.db;

import com.textcon.model.ConversionRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class HistoryDAO {
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

    private final String jdbcUrl;

    public HistoryDAO() {
        this(DBConnection.getInstance().getJdbcUrl());
    }

    public HistoryDAO(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initializeTable();
    }

    public void insertConversion(String original, String converted, String type) {
        insertConversion(original, converted, type, null, null);
    }

    public void insertConversion(String original, String converted, String type, String exportPath, String exportFormat) {
        String sql = """
                INSERT INTO conversion_history
                (original_text, converted_text, conversion_type, export_path, export_format)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, original);
            ps.setString(2, converted);
            ps.setString(3, type);
            ps.setString(4, exportPath);
            ps.setString(5, exportFormat);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to insert conversion history", ex);
        }
    }

    public List<ConversionRecord> getLatest10() {
        String sql = """
                SELECT id, original_text, converted_text, conversion_type, export_path, export_format, created_at
                FROM conversion_history
                ORDER BY id DESC
                LIMIT 10
                """;
        return fetchByQuery(sql);
    }

    public List<ConversionRecord> getAll() {
        String sql = """
                SELECT id, original_text, converted_text, conversion_type, export_path, export_format, created_at
                FROM conversion_history
                ORDER BY id DESC
                """;
        return fetchByQuery(sql);
    }

    private List<ConversionRecord> fetchByQuery(String sql) {
        List<ConversionRecord> records = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                records.add(new ConversionRecord(
                        rs.getInt("id"),
                        rs.getString("original_text"),
                        rs.getString("converted_text"),
                        rs.getString("conversion_type"),
                        rs.getString("export_path"),
                        rs.getString("export_format"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch history", ex);
        }
        return records;
    }

    public void deleteById(int id) {
        String sql = "DELETE FROM conversion_history WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete history row", ex);
        }
    }

    public void clearAll() {
        String sql = "DELETE FROM conversion_history";
        try (Connection conn = getConnection(); Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to clear history", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initializeTable() {
        try (Connection conn = getConnection(); Statement statement = conn.createStatement()) {
            statement.execute(CREATE_TABLE_SQL);
            ensureColumnExists(conn, "export_path", "TEXT");
            ensureColumnExists(conn, "export_format", "VARCHAR(20)");
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize history table", ex);
        }
    }

    private void ensureColumnExists(Connection connection, String columnName, String columnType) throws SQLException {
        if (hasColumn(connection, columnName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE conversion_history ADD COLUMN " + columnName + " " + columnType);
        }
    }

    private boolean hasColumn(Connection connection, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(conversion_history)";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
