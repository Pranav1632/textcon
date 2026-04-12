package com.textcon.db;

import com.textcon.model.ConversionRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryDAOTest {
    private Path dbPath;
    private HistoryDAO historyDAO;

    @BeforeEach
    void setUp() throws IOException {
        dbPath = Files.createTempFile("textcon-history-test", ".db");
        historyDAO = new HistoryDAO("jdbc:sqlite:" + dbPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbPath);
    }

    @Test
    void insertShouldAppearInLatest10() {
        historyDAO.insertConversion("# Hello", "*Hello*", "WhatsApp");

        List<ConversionRecord> rows = historyDAO.getLatest10();

        assertFalse(rows.isEmpty());
        assertEquals("# Hello", rows.get(0).getOriginalText());
        assertEquals("*Hello*", rows.get(0).getConvertedText());
        assertEquals("WhatsApp", rows.get(0).getConversionType());
        assertNull(rows.get(0).getExportPath());
        assertNull(rows.get(0).getExportFormat());
    }

    @Test
    void insertWithExportMetadataShouldPersist() {
        historyDAO.insertConversion("src", "out", "Telegram", "C:\\exports\\result.pdf", "PDF");

        List<ConversionRecord> rows = historyDAO.getAll();

        assertFalse(rows.isEmpty());
        assertEquals("C:\\exports\\result.pdf", rows.get(0).getExportPath());
        assertEquals("PDF", rows.get(0).getExportFormat());
    }

    @Test
    void deleteByIdShouldRemoveRecord() {
        historyDAO.insertConversion("one", "one-out", "WhatsApp");
        historyDAO.insertConversion("two", "two-out", "Slack");

        List<ConversionRecord> rows = historyDAO.getLatest10();
        int idToDelete = rows.get(0).getId();

        historyDAO.deleteById(idToDelete);

        List<ConversionRecord> updated = historyDAO.getLatest10();
        assertTrue(updated.stream().noneMatch(r -> r.getId() == idToDelete));
    }

    @Test
    void clearAllShouldEmptyTable() {
        historyDAO.insertConversion("a", "b", "Discord");
        historyDAO.insertConversion("c", "d", "Telegram");

        historyDAO.clearAll();

        assertTrue(historyDAO.getLatest10().isEmpty());
    }
}
