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
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileExporter {
    public enum ExportTheme {
        BLUE("Blue"),
        CLASSIC("Classic"),
        DARK("Dark");

        private final String label;

        ExportTheme(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static ExportTheme fromLabel(String label) {
            if (label == null) {
                return BLUE;
            }
            for (ExportTheme theme : values()) {
                if (theme.label.equalsIgnoreCase(label)) {
                    return theme;
                }
            }
            return BLUE;
        }
    }

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("^\\s*([-_*])\\1{2,}\\s*$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "\\[([^\\]]+)]\\(([^)]+)\\)|`([^`]+)`|\\*\\*([^*]+)\\*\\*|__([^_]+)__|(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)"
    );

    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11f, Font.NORMAL, BaseColor.BLACK);
    private static final String DOCX_CODE_FONT = "Consolas";

    public void exportTXT(String text, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(text == null ? "" : text);
        }
    }

    public void exportTaggedTXT(String markdownText, File file) throws IOException {
        String tagged = toTaggedText(normalize(markdownText, true));
        exportTXT(tagged, file);
    }

    public void exportPDF(String markdownText, File file) throws IOException {
        exportPDF(markdownText, file, ExportTheme.BLUE);
    }

    public void exportPDF(String markdownText, File file, ExportTheme theme) throws IOException {
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();
            addMarkdownAsPdf(document, normalize(markdownText, false), theme);
        } catch (DocumentException ex) {
            throw new IOException("Failed to export PDF", ex);
        } finally {
            document.close();
        }
    }

    public void exportDOCX(String markdownText, File file) throws IOException {
        exportDOCX(markdownText, file, ExportTheme.BLUE);
    }

    public void exportDOCX(String markdownText, File file, ExportTheme theme) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
            addMarkdownAsDocx(document, normalize(markdownText, false), theme);
            document.write(out);
        }
    }

    public void exportHTML(String markdownText, File file) throws IOException {
        exportHTML(markdownText, file, ExportTheme.BLUE);
    }

    public void exportHTML(String markdownText, File file, ExportTheme theme) throws IOException {
        String html = buildHtml(normalize(markdownText, false), theme);
        exportTXT(html, file);
    }

    public void exportMD(String markdownText, File file) throws IOException {
        exportTXT(normalize(markdownText, false), file);
    }

    public void exportRTF(String text, File file) throws IOException {
        String normalized = text == null ? "" : text.replace("\r\n", "\n");
        StringBuilder rtf = new StringBuilder();
        rtf.append("{\\rtf1\\ansi\\deff0\n");
        rtf.append("{\\fonttbl{\\f0 Segoe UI;}{\\f1 Consolas;}}\n");
        rtf.append("\\f0\\fs22 ");
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\\') {
                rtf.append("\\\\");
            } else if (ch == '{') {
                rtf.append("\\{");
            } else if (ch == '}') {
                rtf.append("\\}");
            } else if (ch == '\n') {
                rtf.append("\\par\n");
            } else if (ch > 127) {
                rtf.append("\\u").append((int) ch).append('?');
            } else {
                rtf.append(ch);
            }
        }
        rtf.append("\n}");
        exportTXT(rtf.toString(), file);
    }

    public void exportJSON(String originalMarkdown, String convertedText, String conversionType, File file) throws IOException {
        String json = "{\n"
                + "  \"conversionType\": \"" + jsonEscape(conversionType) + "\",\n"
                + "  \"generatedAt\": \"" + OffsetDateTime.now() + "\",\n"
                + "  \"originalMarkdown\": \"" + jsonEscape(originalMarkdown) + "\",\n"
                + "  \"convertedText\": \"" + jsonEscape(convertedText) + "\"\n"
                + "}\n";
        exportTXT(json, file);
    }

    private void addMarkdownAsPdf(Document document, String markdown, ExportTheme theme) throws DocumentException {
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
                    addCodeBlock(document, codeBuffer.toString(), theme);
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

            if (isSeparatorLine(trimmed)) {
                if (bulletList != null) {
                    document.add(bulletList);
                    bulletList = null;
                }
                LineSeparator hrLine = new LineSeparator();
                hrLine.setLineColor(hrColor(theme));
                hrLine.setPercentage(93f);
                Paragraph hr = new Paragraph();
                hr.setSpacingBefore(5f);
                hr.setSpacingAfter(7f);
                hr.add(new Chunk(hrLine));
                document.add(hr);
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
                appendInlineMarkdown(heading, headingText, headingFont(level, theme), theme);
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
                appendInlineMarkdown(item, bulletMatcher.group(1).trim(), BODY_FONT, theme);
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
            appendInlineMarkdown(paragraph, line, BODY_FONT, theme);
            document.add(paragraph);
        }

        if (inCodeBlock) {
            addCodeBlock(document, codeBuffer.toString(), theme);
        }
        if (bulletList != null) {
            document.add(bulletList);
        }
    }

    private void addCodeBlock(Document document, String code, ExportTheme theme) throws DocumentException {
        String[] codeLines = normalize(code, false).split("\n", -1);
        Paragraph block = new Paragraph();
        block.setLeading(13f);
        block.setSpacingBefore(4f);
        block.setSpacingAfter(7f);
        Font codeFont = codeFont(theme);
        BaseColor codeBackground = codeBackground(theme);

        for (int i = 0; i < codeLines.length; i++) {
            Chunk chunk = new Chunk(codeLines[i], codeFont);
            chunk.setBackground(codeBackground, 3f, 2f, 3f, 2f);
            block.add(chunk);
            if (i < codeLines.length - 1) {
                block.add(Chunk.NEWLINE);
            }
        }
        document.add(block);
    }

    private void appendInlineMarkdown(Phrase phrase, String text, Font baseFont, ExportTheme theme) {
        Font boldFont = deriveFont(baseFont, Font.BOLD);
        Font italicFont = deriveFont(baseFont, Font.ITALIC);
        Font codeFont = codeFont(theme);
        BaseColor codeBackground = codeBackground(theme);

        Matcher matcher = INLINE_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                phrase.add(new Chunk(text.substring(cursor, matcher.start()), baseFont));
            }

            if (matcher.group(1) != null && matcher.group(2) != null) {
                phrase.add(new Chunk(matcher.group(1) + " (" + matcher.group(2) + ")", baseFont));
            } else if (matcher.group(3) != null) {
                Chunk code = new Chunk(matcher.group(3), codeFont);
                code.setBackground(codeBackground, 2f, 1f, 2f, 1f);
                phrase.add(code);
            } else if (matcher.group(4) != null || matcher.group(5) != null) {
                String boldText = matcher.group(4) != null ? matcher.group(4) : matcher.group(5);
                phrase.add(new Chunk(boldText, boldFont));
            } else if (matcher.group(6) != null || matcher.group(7) != null) {
                String italicText = matcher.group(6) != null ? matcher.group(6) : matcher.group(7);
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

    private void addMarkdownAsDocx(XWPFDocument document, String markdown, ExportTheme theme) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (inCodeBlock) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setSpacingAfter(80);
                XWPFRun run = paragraph.createRun();
                run.setFontFamily(DOCX_CODE_FONT);
                run.setFontSize(10);
                run.setText(line);
                continue;
            }

            if (trimmed.isEmpty()) {
                document.createParagraph();
                continue;
            }

            if (isSeparatorLine(trimmed)) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setSpacingBefore(70);
                paragraph.setSpacingAfter(120);
                XWPFRun run = paragraph.createRun();
                run.setText("----------------------------------------");
                run.setColor(docxSeparatorColor(theme));
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                int level = headingMatcher.group(1).length();
                XWPFParagraph heading = document.createParagraph();
                heading.setSpacingBefore(level <= 2 ? 120 : 80);
                heading.setSpacingAfter(70);
                XWPFRun run = heading.createRun();
                run.setBold(true);
                run.setFontSize(headingFontSize(level));
                run.setColor(docxHeadingColor(theme));
                run.setText(headingMatcher.group(2).trim());
                continue;
            }

            Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
            if (bulletMatcher.find()) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setIndentationLeft(360);
                XWPFRun marker = paragraph.createRun();
                marker.setText("- ");
                appendInlineMarkdown(paragraph, bulletMatcher.group(1).trim());
                continue;
            }

            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(80);
            appendInlineMarkdown(paragraph, line);
        }
    }

    private void appendInlineMarkdown(XWPFParagraph paragraph, String text) {
        Matcher matcher = INLINE_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                XWPFRun plain = paragraph.createRun();
                plain.setText(text.substring(cursor, matcher.start()));
            }

            if (matcher.group(1) != null && matcher.group(2) != null) {
                XWPFRun link = paragraph.createRun();
                link.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                link.setColor("2060D0");
                link.setText(matcher.group(1) + " (" + matcher.group(2) + ")");
            } else if (matcher.group(3) != null) {
                XWPFRun code = paragraph.createRun();
                code.setFontFamily(DOCX_CODE_FONT);
                code.setFontSize(10);
                code.setText(matcher.group(3));
            } else if (matcher.group(4) != null || matcher.group(5) != null) {
                XWPFRun bold = paragraph.createRun();
                bold.setBold(true);
                bold.setText(matcher.group(4) != null ? matcher.group(4) : matcher.group(5));
            } else if (matcher.group(6) != null || matcher.group(7) != null) {
                XWPFRun italic = paragraph.createRun();
                italic.setItalic(true);
                italic.setText(matcher.group(6) != null ? matcher.group(6) : matcher.group(7));
            }
            cursor = matcher.end();
        }

        if (cursor < text.length()) {
            XWPFRun plain = paragraph.createRun();
            plain.setText(text.substring(cursor));
        }
    }

    private int headingFontSize(int level) {
        if (level == 1) {
            return 20;
        }
        if (level == 2) {
            return 17;
        }
        if (level == 3) {
            return 15;
        }
        if (level == 4) {
            return 13;
        }
        return 12;
    }

    private String buildHtml(String markdown, ExportTheme theme) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        StringBuilder html = new StringBuilder();
        String bodyBg = htmlBodyBackground(theme);
        String bodyFg = htmlBodyForeground(theme);
        String codeBg = htmlCodeBackground(theme);
        String headingColor = htmlHeadingColor(theme);
        String hrColor = htmlSeparatorColor(theme);
        html.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>TextCon Export</title>");
        html.append("<style>body{font-family:Segoe UI,Arial,sans-serif;line-height:1.5;margin:24px;background:")
                .append(bodyBg).append(";color:").append(bodyFg)
                .append(";}pre{background:").append(codeBg).append(";padding:10px;border-radius:6px;overflow:auto;}")
                .append("code{font-family:Consolas,monospace;background:").append(codeBg).append(";padding:1px 4px;border-radius:4px;}")
                .append("hr{border:0;border-top:1px solid ").append(hrColor).append(";margin:16px 0;}ul{margin-top:4px;}")
                .append("h1,h2,h3,h4,h5,h6{color:").append(headingColor).append(";}</style>");
        html.append("</head><body>\n");

        boolean inCodeBlock = false;
        boolean inList = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inList) {
                    html.append("</ul>\n");
                    inList = false;
                }
                if (inCodeBlock) {
                    html.append("</code></pre>\n");
                } else {
                    html.append("<pre><code>");
                }
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (inCodeBlock) {
                html.append(escapeHtml(line)).append('\n');
                continue;
            }

            if (trimmed.isEmpty()) {
                if (inList) {
                    html.append("</ul>\n");
                    inList = false;
                }
                continue;
            }

            if (isSeparatorLine(trimmed)) {
                if (inList) {
                    html.append("</ul>\n");
                    inList = false;
                }
                html.append("<hr/>\n");
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                if (inList) {
                    html.append("</ul>\n");
                    inList = false;
                }
                int level = headingMatcher.group(1).length();
                html.append("<h").append(level).append(">")
                        .append(renderInlineHtml(headingMatcher.group(2).trim()))
                        .append("</h").append(level).append(">\n");
                continue;
            }

            Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
            if (bulletMatcher.find()) {
                if (!inList) {
                    html.append("<ul>\n");
                    inList = true;
                }
                html.append("<li>").append(renderInlineHtml(bulletMatcher.group(1).trim())).append("</li>\n");
                continue;
            }

            if (inList) {
                html.append("</ul>\n");
                inList = false;
            }
            html.append("<p>").append(renderInlineHtml(line)).append("</p>\n");
        }

        if (inList) {
            html.append("</ul>\n");
        }
        if (inCodeBlock) {
            html.append("</code></pre>\n");
        }
        html.append("</body></html>\n");
        return html.toString();
    }

    private String renderInlineHtml(String text) {
        String html = escapeHtml(text);

        Matcher linkMatcher = LINK_PATTERN.matcher(html);
        StringBuffer withLinks = new StringBuffer();
        while (linkMatcher.find()) {
            String replacement = "<a href=\"" + escapeHtmlAttribute(linkMatcher.group(2)) + "\">"
                    + escapeHtml(linkMatcher.group(1)) + "</a>";
            linkMatcher.appendReplacement(withLinks, Matcher.quoteReplacement(replacement));
        }
        linkMatcher.appendTail(withLinks);
        html = withLinks.toString();
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__([^_]+)__", "<strong>$1</strong>");
        html = html.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        html = html.replaceAll("(?<!_)_([^_]+)_(?!_)", "<em>$1</em>");
        return html;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeHtmlAttribute(String text) {
        return escapeHtml(text).replace("\"", "&quot;");
    }

    private String jsonEscape(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n");
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '"') {
                escaped.append("\\\"");
            } else if (ch == '\\') {
                escaped.append("\\\\");
            } else if (ch == '\n') {
                escaped.append("\\n");
            } else if (ch == '\t') {
                escaped.append("\\t");
            } else if (ch == '\r') {
                escaped.append("\\r");
            } else if (ch < 0x20) {
                escaped.append(String.format("\\u%04x", (int) ch));
            } else {
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private Font headingFont(int level, ExportTheme theme) {
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
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.BOLD, headingColor(theme));
    }

    private Font codeFont(ExportTheme theme) {
        return FontFactory.getFont(FontFactory.COURIER, 10f, Font.NORMAL, codeForeground(theme));
    }

    private BaseColor codeBackground(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return new BaseColor(40, 46, 58);
        }
        if (theme == ExportTheme.CLASSIC) {
            return new BaseColor(246, 246, 246);
        }
        return new BaseColor(242, 246, 253);
    }

    private BaseColor codeForeground(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return new BaseColor(224, 233, 248);
        }
        if (theme == ExportTheme.CLASSIC) {
            return new BaseColor(34, 34, 34);
        }
        return new BaseColor(28, 52, 84);
    }

    private BaseColor headingColor(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return new BaseColor(112, 180, 255);
        }
        if (theme == ExportTheme.CLASSIC) {
            return new BaseColor(27, 27, 27);
        }
        return new BaseColor(20, 64, 124);
    }

    private BaseColor hrColor(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return new BaseColor(103, 121, 152);
        }
        if (theme == ExportTheme.CLASSIC) {
            return new BaseColor(180, 180, 180);
        }
        return new BaseColor(174, 184, 203);
    }

    private String htmlBodyBackground(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "#1b1f27";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "#ffffff";
        }
        return "#ffffff";
    }

    private String htmlBodyForeground(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "#ecf2ff";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "#222222";
        }
        return "#1b2b42";
    }

    private String htmlCodeBackground(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "#2b3342";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "#f2f2f2";
        }
        return "#f2f6fd";
    }

    private String htmlHeadingColor(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "#79b8ff";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "#222222";
        }
        return "#163f7a";
    }

    private String htmlSeparatorColor(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "#5f7697";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "#cccccc";
        }
        return "#cfd8e8";
    }

    private String docxHeadingColor(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "4FA2FF";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "1F1F1F";
        }
        return "1A4A94";
    }

    private String docxSeparatorColor(ExportTheme theme) {
        if (theme == ExportTheme.DARK) {
            return "7087AA";
        }
        if (theme == ExportTheme.CLASSIC) {
            return "B8B8B8";
        }
        return "AAB7CC";
    }

    private Font deriveFont(Font base, int style) {
        return FontFactory.getFont(base.getFamilyname(), base.getSize(), style, base.getColor());
    }

    private String normalize(String input, boolean removeSeparators) {
        if (input == null) {
            return "";
        }
        String normalized = input.replace("\r\n", "\n");
        if (removeSeparators) {
            normalized = removeSeparatorsOutsideCode(normalized);
        }
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
        return SEPARATOR_PATTERN.matcher(trimmedLine).matches();
    }
}
