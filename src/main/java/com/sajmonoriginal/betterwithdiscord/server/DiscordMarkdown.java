package com.sajmonoriginal.betterwithdiscord.server;

import net.minecraft.core.net.command.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscordMarkdown {
    private static final String FENCE = "```";
    private static final String ESCAPABLE = "\\*_~|`>#-";
    private static final Pattern HEADING = Pattern.compile("^#{1,3} +");
    private static final Pattern SUBTEXT = Pattern.compile("^-# +");
    private static final Pattern QUOTE = Pattern.compile("^>>?>? +");
    private static final Style[] STYLES = {
        new Style("***", TextFormatting.BOLD.toString() + TextFormatting.ITALIC),
        new Style("**", TextFormatting.BOLD.toString()),
        new Style("__", TextFormatting.UNDERLINE.toString()),
        new Style("~~", TextFormatting.STRIKETHROUGH.toString()),
        new Style("||", TextFormatting.OBFUSCATED.toString()),
        new Style("*", TextFormatting.ITALIC.toString()),
        new Style("_", TextFormatting.ITALIC.toString())
    };

    private DiscordMarkdown() {
    }

    static List<String> toGame(List<String> lines) {
        List<String> converted = new ArrayList<>(lines.size());
        boolean code = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(FENCE) && !(trimmed.length() > FENCE.length() * 2 && trimmed.endsWith(FENCE))) {
                code = !code;
                continue;
            }
            converted.add(code ? TextFormatting.LIGHT_GRAY + line : block(line));
        }
        return converted;
    }

    static String line(String line) {
        return block(line);
    }

    private static String block(String line) {
        Matcher heading = HEADING.matcher(line);
        if (heading.find()) {
            return TextFormatting.BOLD + inline(line.substring(heading.end()));
        }
        Matcher subtext = SUBTEXT.matcher(line);
        if (subtext.find()) {
            return TextFormatting.LIGHT_GRAY + inline(line.substring(subtext.end()));
        }
        Matcher quote = QUOTE.matcher(line);
        if (quote.find()) {
            return TextFormatting.LIGHT_GRAY + "| " + TextFormatting.RESET + inline(line.substring(quote.end()));
        }
        return inline(line);
    }

    private static String inline(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && ESCAPABLE.indexOf(text.charAt(i + 1)) >= 0) {
                out.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) {
                    out.append(TextFormatting.PUSH).append(TextFormatting.LIGHT_GRAY).append(text, i + 1, end).append(TextFormatting.POP);
                    i = end + 1;
                    continue;
                }
            }
            int next = styled(text, i, out);
            if (next > i) {
                i = next;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static int styled(String text, int start, StringBuilder out) {
        for (Style style : STYLES) {
            String marker = style.marker();
            if (!text.startsWith(marker, start) || !opens(text, start, marker)) {
                continue;
            }
            int end = closing(text, start + marker.length(), marker);
            if (end < 0) {
                continue;
            }
            out.append(TextFormatting.PUSH).append(style.format())
                .append(inline(text.substring(start + marker.length(), end)))
                .append(TextFormatting.POP);
            return end + marker.length();
        }
        return start;
    }

    private static boolean opens(String text, int start, String marker) {
        int after = start + marker.length();
        if (after >= text.length() || Character.isWhitespace(text.charAt(after))) {
            return false;
        }
        return !"_".equals(marker) || start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
    }

    private static int closing(String text, int from, String marker) {
        char c = marker.charAt(0);
        for (int at = text.indexOf(marker, from + 1); at >= 0; at = text.indexOf(marker, at + 1)) {
            if (Character.isWhitespace(text.charAt(at - 1)) || text.charAt(at - 1) == '\\') {
                continue;
            }
            int after = at + marker.length();
            if (marker.length() == 1 && (text.charAt(at - 1) == c || after < text.length() && text.charAt(after) == c)) {
                continue;
            }
            if ("_".equals(marker) && after < text.length() && Character.isLetterOrDigit(text.charAt(after))) {
                continue;
            }
            return at;
        }
        return -1;
    }

    private record Style(String marker, String format) {
    }
}
