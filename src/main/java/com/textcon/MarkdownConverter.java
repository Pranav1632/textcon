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
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~([^~\\r\\n]+)~~");
    private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|([^|\\r\\n]+)\\|\\|");
    private static final Pattern HTML_UNDERLINE_PATTERN = Pattern.compile("<u>([\\s\\S]*?)</u>");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?s)```[\\t ]*[^\\r\\n`]*\\R?.*?```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("(?<!`)`([^`\\r\\n]+)`(?!`)");
    private static final Pattern SINGLE_TICK_MULTILINE_CODE_PATTERN =
            Pattern.compile("(?<!`)`([A-Za-z0-9#+-]*)\\R([\\s\\S]*?)`(?!`)");
    private static final Pattern BULLET_PATTERN = Pattern.compile("(?m)^\\s*[-*+]\\s+");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern BLOCK_FORMULA_DOLLAR_PATTERN = Pattern.compile("(?s)\\$\\$.*?\\$\\$");
    private static final Pattern BLOCK_FORMULA_BRACKET_PATTERN = Pattern.compile("(?s)\\\\\\[.*?\\\\\\]");
    private static final Pattern INLINE_FORMULA_PAREN_PATTERN = Pattern.compile("\\\\\\((?:[^\\\\]|\\\\.)*?\\\\\\)");
    private static final Pattern INLINE_FORMULA_DOLLAR_PATTERN = Pattern.compile("(?<!\\$)\\$(?!\\s)([^\\r\\n$]+?)\\$(?!\\$)");

    private static final String CODE_BLOCK_TOKEN_PREFIX = "@@CBLOCKTOKEN";
    private static final String INLINE_CODE_TOKEN_PREFIX = "@@ICODETOKEN";
    private static final String FORMULA_TOKEN_PREFIX = "@@FORMULATOKEN";

    public String toWhatsApp(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectSpecialBlocks(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = replace(text, ITALIC_ASTERISK_PATTERN, "__$1__");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "__$1__");
        text = convertHeadings(text, "*", "*");
        text = replace(text, BOLD_ASTERISK_PATTERN, "*$1*");
        text = replace(text, BULLET_PATTERN, "- ");
        text = replace(text, LINK_PATTERN, "$1 ($2)");
        return restoreProtectedSegments(text, placeholders);
    }

    public String toTelegram(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectSpecialBlocks(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = convertHeadings(text, "**", "**");
        text = replace(text, BOLD_ASTERISK_PATTERN, "**$1**");
        text = replace(text, BOLD_UNDERSCORE_PATTERN, "**$1**");
        text = replace(text, ITALIC_ASTERISK_PATTERN, "__$1__");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "__$1__");
        text = replace(text, BULLET_PATTERN, "- ");
        return restoreProtectedSegments(text, placeholders);
    }

    public String toDiscord(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectSpecialBlocks(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = replace(text, HTML_UNDERLINE_PATTERN, "__$1__");
        text = replace(text, BOLD_UNDERSCORE_PATTERN, "__$1__");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "_$1_");
        text = replace(text, STRIKETHROUGH_PATTERN, "~~$1~~");
        text = replace(text, SPOILER_PATTERN, "||$1||");
        text = replace(text, BULLET_PATTERN, "- ");
        text = replace(text, LINK_PATTERN, "[$1]($2)");
        return restoreProtectedSegments(text, placeholders);
    }

    public String toSlack(String input) {
        String text = normalize(input);
        PlaceholderState placeholders = protectSpecialBlocks(text);
        text = placeholders.text();
        text = replaceHorizontalRules(text);
        text = replace(text, ITALIC_ASTERISK_PATTERN, "_$1_");
        text = replace(text, ITALIC_UNDERSCORE_PATTERN, "_$1_");
        text = convertHeadings(text, "*", "*");
        text = replace(text, BOLD_ASTERISK_PATTERN, "*$1*");
        text = replace(text, BOLD_UNDERSCORE_PATTERN, "*$1*");
        text = replace(text, STRIKETHROUGH_PATTERN, "~$1~");
        text = replace(text, BULLET_PATTERN, "* ");
        text = replace(text, LINK_PATTERN, "<$2|$1>");
        text = replace(text, SPOILER_PATTERN, "$1");
        return restoreProtectedSegments(text, placeholders);
    }

    public String toPdf(String input) {
        return normalize(input);
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

    private String replace(String input, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll(replacement);
    }

    private PlaceholderState protectSpecialBlocks(String input) {
        List<String> codeBlocks = new ArrayList<>();
        List<String> inlineCodes = new ArrayList<>();
        List<String> formulas = new ArrayList<>();

        Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(input);
        StringBuffer withCodeTokens = new StringBuffer();
        while (codeMatcher.find()) {
            codeBlocks.add(codeMatcher.group());
            String token = buildToken(CODE_BLOCK_TOKEN_PREFIX, codeBlocks.size() - 1);
            codeMatcher.appendReplacement(withCodeTokens, Matcher.quoteReplacement(token));
        }
        codeMatcher.appendTail(withCodeTokens);

        Matcher inlineMatcher = INLINE_CODE_PATTERN.matcher(withCodeTokens.toString());
        StringBuffer withInlineTokens = new StringBuffer();
        while (inlineMatcher.find()) {
            inlineCodes.add(inlineMatcher.group());
            String token = buildToken(INLINE_CODE_TOKEN_PREFIX, inlineCodes.size() - 1);
            inlineMatcher.appendReplacement(withInlineTokens, Matcher.quoteReplacement(token));
        }
        inlineMatcher.appendTail(withInlineTokens);

        String formulaProtected = protectFormulaPattern(withInlineTokens.toString(), BLOCK_FORMULA_DOLLAR_PATTERN, formulas);
        formulaProtected = protectFormulaPattern(formulaProtected, BLOCK_FORMULA_BRACKET_PATTERN, formulas);
        formulaProtected = protectFormulaPattern(formulaProtected, INLINE_FORMULA_PAREN_PATTERN, formulas);
        formulaProtected = protectInlineDollarFormulas(formulaProtected, formulas);

        return new PlaceholderState(formulaProtected, codeBlocks, inlineCodes, formulas);
    }

    private String protectFormulaPattern(String input, Pattern pattern, List<String> formulas) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            formulas.add(matcher.group());
            String token = buildToken(FORMULA_TOKEN_PREFIX, formulas.size() - 1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String protectInlineDollarFormulas(String input, List<String> formulas) {
        Matcher matcher = INLINE_FORMULA_DOLLAR_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String candidate = matcher.group();
            if (looksLikeFormula(candidate)) {
                formulas.add(candidate);
                String token = buildToken(FORMULA_TOKEN_PREFIX, formulas.size() - 1);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(candidate));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean looksLikeFormula(String candidate) {
        if (candidate == null || candidate.length() < 3) {
            return false;
        }
        String body = candidate.substring(1, candidate.length() - 1);
        return body.contains("\\")
                || body.contains("^")
                || body.contains("_")
                || body.contains("{")
                || body.contains("}")
                || body.contains("=")
                || body.contains("frac")
                || body.contains("sqrt")
                || body.contains("sum")
                || body.contains("int");
    }

    private String restoreProtectedSegments(String input, PlaceholderState placeholders) {
        String restored = restoreTokenGroup(input, CODE_BLOCK_TOKEN_PREFIX, placeholders.codeBlocks());
        restored = restoreTokenGroup(restored, INLINE_CODE_TOKEN_PREFIX, placeholders.inlineCodes());
        return restoreTokenGroup(restored, FORMULA_TOKEN_PREFIX, placeholders.formulas());
    }

    private String restoreTokenGroup(String input, String tokenPrefix, List<String> values) {
        String restored = input;
        for (int i = 0; i < values.size(); i++) {
            String token = buildToken(tokenPrefix, i);
            restored = restored.replace(token, values.get(i));
        }
        return restored;
    }

    private record PlaceholderState(String text, List<String> codeBlocks, List<String> inlineCodes, List<String> formulas) {
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
