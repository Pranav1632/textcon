package com.textcon.util;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.List;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileExporter {
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$");
    private static final Pattern TRIPLE_UNDERSCORE_OR_MORE_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "`([^`]+)`|\\*\\*([^*]+)\\*\\*|__([^_]+)__|(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)"
    );

    private static final BaseColor CODE_BG = new BaseColor(242, 246, 253);
    private static final BaseColor CODE_FG = new BaseColor(28, 52, 84);
    private static final BaseColor HEADING_COLOR = new BaseColor(20, 64, 124);

    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11f, Font.NORMAL, BaseColor.BLACK);
    private static final Font CODE_FONT = FontFactory.getFont(FontFactory.COURIER, 10f, Font.NORMAL, CODE_FG);

    public void exportTXT(String text, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(text == null ? "" : text);
        }
    }

    public void exportTaggedTXT(String markdownText, File file) throws IOException {
        String tagged = toTaggedText(normalize(markdownText));
        exportTXT(tagged, file);
    }

    public void exportPDF(String markdownText, File file) throws IOException {
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();
            addMarkdownAsPdf(document, normalize(markdownText));
        } catch (DocumentException ex) {
            throw new IOException("Failed to export PDF", ex);
        } finally {
            document.close();
        }
    }

    private void addMarkdownAsPdf(Document document, String markdown) throws DocumentException {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();
        List bulletList = null;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (bulletList != null) {
                    document.add(bulletList);
                    bulletList = null;
                }

                if (inCodeBlock) {
                    addCodeBlock(document, codeBuffer.toString());
                    codeBuffer.setLength(0);
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                if (codeBuffer.length() > 0) {
                    codeBuffer.append('\n');
                }
                codeBuffer.append(line);
                continue;
            }

            if (trimmed.isEmpty()) {
                if (bulletList != null) {
                    document.add(bulletList);
                    bulletList = null;
                }
                Paragraph spacer = new Paragraph(" ", BODY_FONT);
                spacer.setSpacingAfter(2f);
                document.add(spacer);
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                if (bulletList != null) {
                    document.add(bulletList);
                    bulletList = null;
                }
                int level = headingMatcher.group(1).length();
                String headingText = headingMatcher.group(2).trim();
                Paragraph heading = new Paragraph();
                heading.setSpacingBefore(level <= 2 ? 8f : 5f);
                heading.setSpacingAfter(4f);
                appendInlineMarkdown(heading, headingText, headingFont(level));
                document.add(heading);
                continue;
            }

            Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
            if (bulletMatcher.find()) {
                if (bulletList == null) {
                    bulletList = new List(List.UNORDERED);
                    bulletList.setIndentationLeft(18f);
                    bulletList.setAutoindent(false);
                }
                ListItem item = new ListItem();
                item.setLeading(16f);
                appendInlineMarkdown(item, bulletMatcher.group(1).trim(), BODY_FONT);
                bulletList.add(item);
                continue;
            }

            if (bulletList != null) {
                document.add(bulletList);
                bulletList = null;
            }

            Paragraph paragraph = new Paragraph();
            paragraph.setLeading(17f);
            paragraph.setSpacingAfter(5f);
            appendInlineMarkdown(paragraph, line, BODY_FONT);
            document.add(paragraph);
        }

        if (inCodeBlock) {
            addCodeBlock(document, codeBuffer.toString());
        }
        if (bulletList != null) {
            document.add(bulletList);
        }
    }

    private void addCodeBlock(Document document, String code) throws DocumentException {
        String[] codeLines = normalize(code).split("\n", -1);
        Paragraph block = new Paragraph();
        block.setLeading(13f);
        block.setSpacingBefore(4f);
        block.setSpacingAfter(7f);

        for (int i = 0; i < codeLines.length; i++) {
            Chunk chunk = new Chunk(codeLines[i], CODE_FONT);
            chunk.setBackground(CODE_BG, 3f, 2f, 3f, 2f);
            block.add(chunk);
            if (i < codeLines.length - 1) {
                block.add(Chunk.NEWLINE);
            }
        }
        document.add(block);
    }

    private void appendInlineMarkdown(Phrase phrase, String text, Font baseFont) {
        Font boldFont = deriveFont(baseFont, Font.BOLD);
        Font italicFont = deriveFont(baseFont, Font.ITALIC);

        Matcher matcher = INLINE_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                phrase.add(new Chunk(text.substring(cursor, matcher.start()), baseFont));
            }

            if (matcher.group(1) != null) {
                Chunk code = new Chunk(matcher.group(1), CODE_FONT);
                code.setBackground(CODE_BG, 2f, 1f, 2f, 1f);
                phrase.add(code);
            } else if (matcher.group(2) != null || matcher.group(3) != null) {
                String boldText = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                phrase.add(new Chunk(boldText, boldFont));
            } else if (matcher.group(4) != null || matcher.group(5) != null) {
                String italicText = matcher.group(4) != null ? matcher.group(4) : matcher.group(5);
                phrase.add(new Chunk(italicText, italicFont));
            }
            cursor = matcher.end();
        }

        if (cursor < text.length()) {
            phrase.add(new Chunk(text.substring(cursor), baseFont));
        }
    }

    private String toTaggedText(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        StringBuilder tagged = new StringBuilder();
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    tagged.append("[/CODE]");
                } else {
                    tagged.append("[CODE]");
                }
                tagged.append(System.lineSeparator());
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (inCodeBlock) {
                tagged.append(line).append(System.lineSeparator());
                continue;
            }

            if (trimmed.isEmpty()) {
                tagged.append(System.lineSeparator());
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                int level = headingMatcher.group(1).length();
                tagged.append("[H").append(level).append("] ")
                        .append(headingMatcher.group(2).trim())
                        .append(System.lineSeparator());
                continue;
            }

            Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
            if (bulletMatcher.find()) {
                tagged.append("[LI] ").append(bulletMatcher.group(1).trim()).append(System.lineSeparator());
                continue;
            }

            tagged.append("[P] ").append(line).append(System.lineSeparator());
        }

        if (inCodeBlock) {
            tagged.append("[/CODE]").append(System.lineSeparator());
        }
        return tagged.toString();
    }

    private Font headingFont(int level) {
        float size;
        if (level == 1) {
            size = 19f;
        } else if (level == 2) {
            size = 17f;
        } else if (level == 3) {
            size = 15f;
        } else if (level == 4) {
            size = 13.5f;
        } else if (level == 5) {
            size = 12.5f;
        } else {
            size = 11.5f;
        }
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.BOLD, HEADING_COLOR);
    }

    private Font deriveFont(Font base, int style) {
        return FontFactory.getFont(base.getFamilyname(), base.getSize(), style, base.getColor());
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.replace("\r\n", "\n");
        normalized = TRIPLE_UNDERSCORE_OR_MORE_PATTERN.matcher(normalized).replaceAll("__");
        normalized = removeSeparatorsOutsideCode(normalized);
        return normalized;
    }

    private String removeSeparatorsOutsideCode(String input) {
        String[] lines = input.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                sb.append(line).append('\n');
                continue;
            }

            if (!inCodeBlock && isSeparatorLine(trimmed)) {
                continue;
            }
            sb.append(line).append('\n');
        }

        String cleaned = sb.toString().replaceAll("(?m)(\\n\\s*){3,}", "\n\n");
        if (cleaned.endsWith("\n")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private boolean isSeparatorLine(String trimmedLine) {
        if (trimmedLine.length() < 3) {
            return false;
        }
        char first = trimmedLine.charAt(0);
        if (first != '-' && first != '_' && first != '*') {
            return false;
        }
        for (int i = 1; i < trimmedLine.length(); i++) {
            if (trimmedLine.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }
}
