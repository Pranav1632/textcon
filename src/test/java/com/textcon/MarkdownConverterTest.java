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
        String input = "# Heading\n\n```java\nint x = 1;\n```\n*italic*\n---";
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
    void telegramShouldKeepMarkdownCodeFences() {
        String input = """
                ```java
                int total = left + right;
                ```
                """;

        String output = converter.toTelegram(input);

        assertTrue(output.contains("```java"));
        assertTrue(output.contains("int total = left + right;"));
        assertTrue(output.contains("```"));
        assertFalse(output.contains("<code>"));
    }

    @Test
    void telegramShouldKeepMarkdownLinksAndFormula() {
        String input = "Use [docs](https://example.com) and formula $x_i = \\\\frac{-b}{2a}$";
        String output = converter.toTelegram(input);

        assertTrue(output.contains("[docs](https://example.com)"));
        assertTrue(output.contains("$x_i = \\\\frac{-b}{2a}$"));
    }

    @Test
    void shouldReplaceTripleDashAndTripleUnderscoreSeparatorForWhatsApp() {
        String input = """
                Summary
                ---
                Keep this line
                ___
                """;
        String output = converter.toWhatsApp(input);

        assertFalse(output.contains("\n---\n"));
        assertFalse(output.contains("\n___\n"));
        assertTrue(output.contains("Summary"));
        assertTrue(output.contains("Keep this line"));
    }

    @Test
    void discordShouldKeepExpectedMarkdownSyntax() {
        String input = """
                # Header
                **Bold**
                *Italic*
                __Underlined__
                ~~Strikethrough~~
                ||Hidden||
                [Site](https://example.com)
                > Quote
                - Item
                1. Numbered
                """;

        String output = converter.toDiscord(input);

        assertTrue(output.contains("# Header"));
        assertTrue(output.contains("**Bold**"));
        assertTrue(output.contains("*Italic*"));
        assertTrue(output.contains("__Underlined__"));
        assertTrue(output.contains("~~Strikethrough~~"));
        assertTrue(output.contains("||Hidden||"));
        assertTrue(output.contains("[Site](https://example.com)"));
        assertTrue(output.contains("> Quote"));
        assertTrue(output.contains("- Item"));
        assertTrue(output.contains("1. Numbered"));
    }

    @Test
    void slackShouldUseMrkdwnRules() {
        String input = """
                # Header
                **Bold**
                *Italic*
                ~~Strike~~
                [Docs](https://example.com)
                > Quote
                - Item
                1. Numbered
                """;

        String output = converter.toSlack(input);

        assertTrue(output.contains("*Header*"));
        assertTrue(output.contains("*Bold*"));
        assertTrue(output.contains("_Italic_"));
        assertTrue(output.contains("~Strike~"));
        assertTrue(output.contains("<https://example.com|Docs>"));
        assertTrue(output.contains("> Quote"));
        assertTrue(output.contains("* Item"));
        assertTrue(output.contains("1. Numbered"));
    }
}
