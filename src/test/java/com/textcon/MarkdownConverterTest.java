package com.textcon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownConverterTest {
    private final MarkdownConverter converter = new MarkdownConverter();

    @Test
    void shouldConvertHeadingToWhatsApp() {
        assertEquals("*Hello*", converter.toWhatsApp("# Hello"));
    }

    @Test
    void shouldConvertBoldToWhatsApp() {
        assertEquals("*Bold*", converter.toWhatsApp("**Bold**"));
    }

    @Test
    void shouldConvertCodeBlockToTripleBackticksForWhatsApp() {
        assertEquals("```code```", converter.toWhatsApp("```code```"));
    }

    @Test
    void shouldReturnEmptyOnEmptyString() {
        assertEquals("", converter.toWhatsApp(""));
    }

    @Test
    void shouldReturnEmptyOnNullInput() {
        assertEquals("", converter.toWhatsApp(null));
    }

    @Test
    void shouldConvertMixedContent() {
        String input = "# Title\n**bold** and *italic* with `code`";
        String output = converter.toWhatsApp(input);

        assertTrue(output.contains("*Title*"));
        assertTrue(output.contains("*bold*"));
        assertTrue(output.contains("__italic__"));
        assertTrue(output.contains("`code`"));
    }

    @Test
    void shouldConvertSingleUnderscoreItalicToDoubleUnderscoreForWhatsApp() {
        assertEquals("__word__", converter.toWhatsApp("_word_"));
    }

    @Test
    void shouldNotCorruptCodeOperatorsInsideCodeBlockForWhatsApp() {
        String input = "```java\nwhile (table[(index + i * i) % SIZE] != -1) {\n    i++;\n}\n```";
        String output = converter.toWhatsApp(input);

        assertTrue(output.contains("i * i"));
        assertTrue(output.startsWith("```"));
        assertTrue(output.endsWith("```"));
    }

    @Test
    void pdfConversionShouldPreserveInputAsIs() {
        String input = "# Heading\n\n```java\nint x = 1;\n```\n*italic*";
        assertEquals(input, converter.toPdf(input));
    }

    @Test
    void shouldNormalizeSingleBacktickMultilineCodeToTripleBackticks() {
        String input = "`java\nint total = a * b;\n`";
        String output = converter.toWhatsApp(input);

        assertTrue(output.startsWith("```java"));
        assertTrue(output.contains("a * b"));
        assertTrue(output.endsWith("```"));
    }

    @Test
    void shouldNotLeakCodePlaceholdersInWhatsAppOutput() {
        String input = """
                # Title
                ```bash
                pnpm add next-auth
                ```

                ```bash
                pnpm remove chart.js
                ```
                """;
        String output = converter.toWhatsApp(input);

        assertTrue(output.contains("```bash"));
        assertTrue(output.contains("pnpm add next-auth"));
        assertTrue(output.contains("pnpm remove chart.js"));
        assertTrue(!output.contains("@@CBLOCKTOKEN"));
        assertTrue(!output.contains("@@ICODETOKEN"));
    }

    @Test
    void shouldRemoveTripleUnderscoreForAllPlatforms() {
        String input = "Title ___bad___ text";

        assertFalse(converter.toWhatsApp(input).contains("___"));
        assertFalse(converter.toTelegram(input).contains("___"));
        assertFalse(converter.toDiscord(input).contains("___"));
        assertFalse(converter.toSlack(input).contains("___"));
        assertFalse(converter.toPdf(input).contains("___"));
    }

    @Test
    void shouldReplaceTripleDashAndTripleUnderscoreSeparatorForWhatsApp() {
        String input = """
                3. ⚠️ What I fixed
                ---
                👉 No need to rewrite command
                ___
                """;
        String output = converter.toWhatsApp(input);

        assertFalse(output.contains("\n---\n"));
        assertFalse(output.contains("\n___\n"));
        assertFalse(output.contains("• • •"));
        assertTrue(output.contains("3. ⚠️ What I fixed"));
        assertTrue(output.contains("👉 No need to rewrite command"));
    }
}
