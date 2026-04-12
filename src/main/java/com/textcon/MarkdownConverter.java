package com.textcon;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownConverter {
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*#{1,6}\\s+(.+)$");
    private static final Pattern BOLD_ASTERISK_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE_PATTERN = Pattern.compile("__([^_]+)__");
    private static final Pattern ITALIC_ASTERISK_PATTERN = Pattern.compile("(?<!\\*)\\*([^*\\r\\n]+)\\*(?!\\*)");
    private static final Pattern ITALIC_UNDERSCORE_PATTERN = Pattern.compile("(?<!_)_([^_\\r\\n]+)_(?!_)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern SINGLE_TICK_MULTILINE_CODE_PATTERN =
            Pattern.compile("(?<!`)`([A-Za-z0-9#+-]*)\\R([\\s\\S]*?)`(?!`)");
    private static final Pattern TRIPLE_UNDERSCORE_OR_MORE_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BULLET_PATTERN = Pattern.compile("(?m)^\\s*[-*+]\\s+");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final String CODE_BLOCK_TOKEN_PREFIX = "@@CBLOCKTOKEN";
    private static final String INLINE_CODE_TOKEN_PREFIX = "@@ICODETOKEN";

    public String toWhatsApp(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectCode(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = replace(text, ITALIC_ASTERISK_PATTERN, "__$1__");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "__$1__");
        text = convertHeadings(text, "*", "*");
        text = replace(text, BOLD_ASTERISK_PATTERN, "*$1*");
        text = replace(text, BULLET_PATTERN, "- ");
        text = replace(text, LINK_PATTERN, "$1 ($2)");
        text = restoreCodeForWhatsApp(text, placeholders.codeBlocks(), placeholders.inlineCodes());
        return sanitizeUnderscoreRuns(text);
    }

    public String toTelegram(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectCode(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = convertHeadings(text, "<b>", "</b>");
        text = replace(text, BOLD_ASTERISK_PATTERN, "<b>$1</b>");
        text = replace(text, BOLD_UNDERSCORE_PATTERN, "<b>$1</b>");
        text = replace(text, ITALIC_ASTERISK_PATTERN, "<i>$1</i>");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "<i>$1</i>");
        text = replace(text, BULLET_PATTERN, "- ");
        text = replace(text, LINK_PATTERN, "<a href=\"$2\">$1</a>");
        text = restoreCodeForHtml(text, placeholders.codeBlocks(), placeholders.inlineCodes());
        return sanitizeUnderscoreRuns(text);
    }

    public String toDiscord(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectCode(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = convertHeadings(text, "**", "**");
        text = replace(text, BOLD_UNDERSCORE_PATTERN, "**$1**");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "*$1*");
        text = replace(text, BULLET_PATTERN, "- ");
        text = replace(text, LINK_PATTERN, "$1 ($2)");
        text = restoreCodeForFenced(text, placeholders.codeBlocks(), placeholders.inlineCodes(), "`", "`");
        return sanitizeUnderscoreRuns(text);
    }

    public String toSlack(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectCode(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = replace(text, ITALIC_ASTERISK_PATTERN, "_$1_");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "_$1_");
        text = convertHeadings(text, "*", "*");
        text = replace(text, BOLD_ASTERISK_PATTERN, "*$1*");
        text = replace(text, BOLD_UNDERSCORE_PATTERN, "*$1*");
        text = replace(text, BULLET_PATTERN, "- ");
        text = replace(text, LINK_PATTERN, "$1 ($2)");
        text = restoreCodeForFenced(text, placeholders.codeBlocks(), placeholders.inlineCodes(), "`", "`");
        return sanitizeUnderscoreRuns(text);
    }

    public String toPdf(String input) {
        String text = normalize(input);
        text = sanitizeUnderscoreRuns(text);
        return replaceHorizontalRules(text);
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.replace("\r\n", "\n");
        return normalizeSingleTickMultilineFences(normalized);
    }

    private String convertHeadings(String input, String prefix, String suffix) {
        Matcher matcher = HEADING_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + title + suffix));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String convertCodeBlocks(String input, String wrapper) {
        return convertCodeBlocks(input, wrapper, wrapper);
    }

    private String convertCodeBlocks(String input, String prefix, String suffix) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String code = matcher.group(1).trim();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + code + suffix));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replace(String input, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll(replacement);
    }

    private PlaceholderState protectCode(String input) {
        List<String> codeBlocks = new ArrayList<>();
        List<String> inlineCodes = new ArrayList<>();

        Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(input);
        StringBuffer withCodeTokens = new StringBuffer();
        while (codeMatcher.find()) {
            codeBlocks.add(codeMatcher.group(1).trim());
            String token = buildToken(CODE_BLOCK_TOKEN_PREFIX, codeBlocks.size() - 1);
            codeMatcher.appendReplacement(withCodeTokens, Matcher.quoteReplacement(token));
        }
        codeMatcher.appendTail(withCodeTokens);

        Matcher inlineMatcher = INLINE_CODE_PATTERN.matcher(withCodeTokens.toString());
        StringBuffer withInlineTokens = new StringBuffer();
        while (inlineMatcher.find()) {
            inlineCodes.add(inlineMatcher.group(1));
            String token = buildToken(INLINE_CODE_TOKEN_PREFIX, inlineCodes.size() - 1);
            inlineMatcher.appendReplacement(withInlineTokens, Matcher.quoteReplacement(token));
        }
        inlineMatcher.appendTail(withInlineTokens);

        return new PlaceholderState(withInlineTokens.toString(), codeBlocks, inlineCodes);
    }

    private String restoreCodeForWhatsApp(String text, List<String> codeBlocks, List<String> inlineCodes) {
        String restored = text;
        for (int i = 0; i < codeBlocks.size(); i++) {
            String token = buildToken(CODE_BLOCK_TOKEN_PREFIX, i);
            restored = restored.replace(token, "```" + codeBlocks.get(i) + "```");
        }
        for (int i = 0; i < inlineCodes.size(); i++) {
            String token = buildToken(INLINE_CODE_TOKEN_PREFIX, i);
            restored = restored.replace(token, "`" + inlineCodes.get(i) + "`");
        }
        return restored;
    }

    private String restoreCodeForHtml(String text, List<String> codeBlocks, List<String> inlineCodes) {
        String restored = text;
        for (int i = 0; i < codeBlocks.size(); i++) {
            String token = buildToken(CODE_BLOCK_TOKEN_PREFIX, i);
            restored = restored.replace(token, "<code>" + codeBlocks.get(i) + "</code>");
        }
        for (int i = 0; i < inlineCodes.size(); i++) {
            String token = buildToken(INLINE_CODE_TOKEN_PREFIX, i);
            restored = restored.replace(token, "<code>" + inlineCodes.get(i) + "</code>");
        }
        return restored;
    }

    private String restoreCodeForFenced(String text, List<String> codeBlocks, List<String> inlineCodes, String prefix, String suffix) {
        String restored = text;
        for (int i = 0; i < codeBlocks.size(); i++) {
            String token = buildToken(CODE_BLOCK_TOKEN_PREFIX, i);
            restored = restored.replace(token, prefix + codeBlocks.get(i) + suffix);
        }
        for (int i = 0; i < inlineCodes.size(); i++) {
            String token = buildToken(INLINE_CODE_TOKEN_PREFIX, i);
            restored = restored.replace(token, prefix + inlineCodes.get(i) + suffix);
        }
        return restored;
    }

    private record PlaceholderState(String text, List<String> codeBlocks, List<String> inlineCodes) {
    }

    private String normalizeSingleTickMultilineFences(String input) {
        Matcher matcher = SINGLE_TICK_MULTILINE_CODE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String lang = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String code = matcher.group(2) == null ? "" : matcher.group(2);
            String opening = lang.isBlank() ? "```" : "```" + lang;
            String replacement = opening + "\n" + code.trim() + "\n```";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String sanitizeUnderscoreRuns(String input) {
        return TRIPLE_UNDERSCORE_OR_MORE_PATTERN.matcher(input).replaceAll("__");
    }

    private String replaceHorizontalRules(String input) {
        String normalized = input.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
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

        String cleaned = sb.toString();
        cleaned = cleaned.replaceAll("(?m)(\\n\\s*){3,}", "\n\n");
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

    private String buildToken(String prefix, int index) {
        return prefix + index + "@@";
    }
}
