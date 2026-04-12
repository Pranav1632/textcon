package com.textcon.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileExporterTest {
    private final FileExporter exporter = new FileExporter();

    @Test
    void taggedTextExportShouldContainStructuralTags() throws IOException {
        Path file = Files.createTempFile("textcon-tagged", ".txt");
        try {
            String markdown = """
                    # Heading

                    Paragraph
                    - item
                    ```java
                    System.out.println("Hello");
                    ```
                    """;
            exporter.exportTaggedTXT(markdown, file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);

            assertTrue(exported.contains("[H1] Heading"));
            assertTrue(exported.contains("[P] Paragraph"));
            assertTrue(exported.contains("[LI] item"));
            assertTrue(exported.contains("[CODE]"));
            assertTrue(exported.contains("[/CODE]"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void taggedTextExportShouldCollapseTripleUnderscores() throws IOException {
        Path file = Files.createTempFile("textcon-tagged-underscore", ".txt");
        try {
            exporter.exportTaggedTXT("Paragraph ___broken___ text", file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(!exported.contains("___"));
            assertTrue(exported.contains("__broken__"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void taggedTextExportShouldRemoveSeparatorLines() throws IOException {
        Path file = Files.createTempFile("textcon-tagged-separators", ".txt");
        try {
            String markdown = "Line A\n---\nLine B\n___\nLine C\n***\n";
            exporter.exportTaggedTXT(markdown, file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);

            assertTrue(!exported.contains("\n---\n"));
            assertTrue(!exported.contains("\n___\n"));
            assertTrue(!exported.contains("\n***\n"));
            assertTrue(exported.contains("Line A"));
            assertTrue(exported.contains("Line B"));
            assertTrue(exported.contains("Line C"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void pdfExportShouldCreateAFile() throws IOException {
        Path file = Files.createTempFile("textcon-pdf", ".pdf");
        try {
            exporter.exportPDF("# Heading\nBody with `code`", file.toFile());
            assertTrue(Files.size(file) > 0);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
