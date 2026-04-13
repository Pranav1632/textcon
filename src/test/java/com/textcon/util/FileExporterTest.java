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
    void markdownExportShouldKeepSectionSeparators() throws IOException {
        Path file = Files.createTempFile("textcon-md", ".md");
        try {
            String markdown = "One\n---\nTwo\n___\n";
            exporter.exportMD(markdown, file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);

            assertTrue(exported.contains("\n---\n"));
            assertTrue(exported.contains("\n___\n"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void htmlExportShouldContainHrForMarkdownSeparators() throws IOException {
        Path file = Files.createTempFile("textcon-html", ".html");
        try {
            exporter.exportHTML("A\n---\nB", file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);

            assertTrue(exported.contains("<hr/>"));
            assertTrue(exported.contains("<p>A</p>"));
            assertTrue(exported.contains("<p>B</p>"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void jsonExportShouldContainMetadataAndText() throws IOException {
        Path file = Files.createTempFile("textcon-json", ".json");
        try {
            exporter.exportJSON("# H", "*H*", "WhatsApp", file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);

            assertTrue(exported.contains("\"conversionType\": \"WhatsApp\""));
            assertTrue(exported.contains("\"originalMarkdown\": \"# H\""));
            assertTrue(exported.contains("\"convertedText\": \"*H*\""));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rtfExportShouldCreateRtfDocument() throws IOException {
        Path file = Files.createTempFile("textcon-rtf", ".rtf");
        try {
            exporter.exportRTF("Line 1\nLine 2", file.toFile());
            String exported = Files.readString(file, StandardCharsets.UTF_8);

            assertTrue(exported.startsWith("{\\rtf1"));
            assertTrue(exported.contains("\\par"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void docxAndPdfExportShouldCreateFiles() throws IOException {
        Path docx = Files.createTempFile("textcon-word", ".docx");
        Path pdf = Files.createTempFile("textcon-pdf", ".pdf");
        try {
            String markdown = "# Heading\n---\nBody with `code`";
            exporter.exportDOCX(markdown, docx.toFile(), FileExporter.ExportTheme.DARK);
            exporter.exportPDF(markdown, pdf.toFile(), FileExporter.ExportTheme.DARK);

            assertTrue(Files.size(docx) > 0);
            assertTrue(Files.size(pdf) > 0);
        } finally {
            Files.deleteIfExists(docx);
            Files.deleteIfExists(pdf);
        }
    }
}
