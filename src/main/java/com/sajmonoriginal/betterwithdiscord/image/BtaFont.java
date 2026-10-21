package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BtaFont {
    public static final int LINE_HEIGHT = 9;

    private static final String FORMAT_CODES = "0123456789abcdefklmnopr+-";
    private static final char[] BLACKLIST = {'，', '。', '、', '【', '】', '；', '’', '‘', '“', '”', '？', '！', '·'};
    private static final int CELL = 16;
    private static final int ALPHA_TEST = 26;
    private static final long ITALIC = 1L << 32;
    private static final long UNDERLINE = 1L << 33;
    private static final long STRIKETHROUGH = 1L << 34;
    private static final long BOLD = 1L << 35;
    private static final long OUTLINE = 1L << 37;
    private static final long COLOR = 0xFFFFFFFFL;

    private final ClientAssets assets;
    private final int[] defaultIndices = new int[65536];
    private final byte[] defaultWidths = new byte[65536];
    private final byte[] unicodeWidths = new byte[65536];
    private final int[] chatColors = new int[16];

    public BtaFont(ClientAssets assets) throws IOException {
        this.assets = assets;
        Arrays.fill(defaultIndices, -1);
        byte[] charList = assets.read("font.txt");
        if (charList == null) {
            throw new IOException("font.txt is missing from the BTA client");
        }
        StringBuilder chars = new StringBuilder();
        for (String line : new String(charList, StandardCharsets.UTF_8).split("\\R")) {
            if (!line.startsWith("#")) {
                chars.append(line);
            }
        }
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (defaultIndices[c] == -1) {
                defaultIndices[c] = i;
            }
        }
        for (char c : BLACKLIST) {
            defaultIndices[c] = -1;
        }
        readWidths("assets/minecraft/textures/gui/font/default/glyph_sizes.bin", defaultWidths);
        readWidths("assets/minecraft/textures/gui/font/unicode/glyph_sizes.bin", unicodeWidths);
        BufferedImage colors = assets.image("assets/minecraft/textures/misc/colors_chat.png");
        for (int i = 0; i < chatColors.length; i++) {
            chatColors[i] = colors == null ? 0xFFFFFFFF : colors.getRGB(i, 0);
        }
    }

    private void readWidths(String path, byte[] target) throws IOException {
        byte[] data = assets.read(path);
        if (data == null) {
            throw new IOException(path + " is missing from the BTA client");
        }
        System.arraycopy(data, 0, target, 0, Math.min(data.length, target.length));
    }

    public int chatColor(int index) {
        return chatColors[Math.max(0, Math.min(15, index))];
    }

    public static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        String[] parts = text.replace("\t", "   ").split("\n", -1);
        for (int i = 0; i < parts.length - 1; i++) {
            lines.add(parts[i]);
        }
        String last = parts[parts.length - 1].trim();
        if (!last.isEmpty()) {
            lines.add(last);
        }
        return lines;
    }

    private static int formatLength(CharSequence text, int i) {
        if (text.charAt(i) != '§' || i + 1 >= text.length()) {
            return 0;
        }
        char next = text.charAt(i + 1);
        if (next == '<') {
            for (int j = i + 2; j < text.length() && j < i + 11; j++) {
                if (text.charAt(j) == '>') {
                    return j - i + 1;
                }
            }
            return 0;
        }
        return FORMAT_CODES.indexOf(next) != -1 ? 2 : 0;
    }

    public void draw(Canvas canvas, String text, double x, double y, int baseColor, boolean shadow) {
        drawLines(canvas, List.of(text), x, y, baseColor, shadow);
    }

    public double measure(List<String> lines) {
        return drawLines(null, lines, 0, 0, 0xFFFFFFFF, false);
    }

    public double drawLines(Canvas canvas, List<String> lines, double x, double y, int baseColor, boolean shadow) {
        long base = Integer.toUnsignedLong(baseColor);
        long config = base;
        long[] stack = new long[32];
        int stackIndex = 0;
        double currentY = y;
        double widest = 0;
        for (String text : lines) {
            double currentX = x;
            for (int i = 0; i < text.length(); i++) {
                int skip = formatLength(text, i);
                if (skip > 0) {
                    char code = text.charAt(i + 1);
                    if (code == '<') {
                        config = config & ~COLOR | hexColor(text.substring(i + 2, i + skip - 1), baseColor) & COLOR;
                    } else if (code == '+') {
                        if (stackIndex != stack.length - 1) {
                            stack[stackIndex++] = config;
                        }
                    } else if (code == '-') {
                        if (stackIndex != 0) {
                            config = stack[--stackIndex];
                        }
                    } else if (code == 'r') {
                        config = base;
                    } else {
                        int index = FORMAT_CODES.indexOf(code);
                        if (index < 16) {
                            int alpha = baseColor & 0xFF000000;
                            config = config & ~COLOR | Integer.toUnsignedLong(chatColors[index] & 0xFFFFFF | (alpha == 0 ? 0xFF000000 : alpha));
                        } else {
                            config |= switch (code) {
                                case 'l' -> BOLD;
                                case 'm' -> STRIKETHROUGH;
                                case 'n' -> UNDERLINE;
                                case 'o' -> ITALIC;
                                case 'p' -> OUTLINE;
                                default -> 0L;
                            };
                        }
                    }
                    i += skip - 1;
                    continue;
                }
                char c = text.charAt(i);
                if (canvas != null) {
                    drawGlyph(canvas, glyph(c), currentX, currentY, config, shadow);
                }
                currentX += advance(c) + ((config & BOLD) != 0 ? 1 : 0);
            }
            widest = Math.max(widest, currentX - x);
            currentY += LINE_HEIGHT;
        }
        return widest;
    }

    private static long hexColor(String hex, int baseColor) {
        long value = 0xFFFFFFL;
        try {
            value = Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            value = 0xFFFFFFL;
        }
        if ((value & 0xFF000000L) == 0) {
            return value & 0xFFFFFFL | Integer.toUnsignedLong(baseColor & 0xFF000000);
        }
        return value & COLOR;
    }

    private double advance(char c) {
        if (c == ' ') {
            return 4.0;
        }
        Glyph glyph = glyph(c);
        if (glyph == null) {
            return 0.0;
        }
        return glyph.width / 2.0 + 1.0;
    }

    private Glyph glyph(char c) {
        if (c == ' ') {
            return null;
        }
        int index = defaultIndices[c];
        if (index >= 0) {
            BufferedImage page = page("default/font", index / 256);
            if (page != null) {
                return glyph(page, index, defaultWidths[index]);
            }
        }
        BufferedImage page = page("unicode/glyph", c / 256);
        return page == null ? null : glyph(page, c, unicodeWidths[c]);
    }

    private static Glyph glyph(BufferedImage page, int index, byte size) {
        int upper = size >> 4;
        int lower = size & 15;
        int width = lower + 1 - upper;
        if (width <= 0) {
            return null;
        }
        return new Glyph(page, index % 16 * CELL + upper, (index & 0xFF) / 16 * CELL, width);
    }

    private BufferedImage page(String prefix, int page) {
        return assets.image(String.format("assets/minecraft/textures/gui/font/%s_%02X.png", prefix, page));
    }

    private void drawGlyph(Canvas canvas, Glyph glyph, double x, double y, long config, boolean shadow) {
        if (glyph == null) {
            return;
        }
        int color = (int) (config & COLOR);
        if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }
        int shade = color & 0xFF000000 | (color >> 16 & 0xFF) / 4 << 16 | (color >> 8 & 0xFF) / 4 << 8 | (color & 0xFF) / 4;
        int originX = (int) Math.round(x * Canvas.SCALE);
        int originY = (int) Math.round(y * Canvas.SCALE);
        boolean italic = (config & ITALIC) != 0;
        for (int ty = -1; ty <= CELL + 1; ty++) {
            int shift = italic ? (int) Math.round(1.0 - 2.0 * (ty + 1.5) / (CELL + 3)) : 0;
            for (int tx = -1; tx <= glyph.width + 1; tx++) {
                int argb = colorAt(glyph, tx, ty, config, color);
                if ((argb >>> 24) < ALPHA_TEST) {
                    if ((config & OUTLINE) != 0) {
                        argb = touchesGlyph(glyph, tx, ty, config, color) ? shade : 0;
                    } else if (shadow && (colorAt(glyph, tx - 2, ty - 2, config, color) >>> 24) > ALPHA_TEST) {
                        argb = shade;
                    }
                }
                if ((argb >>> 24) >= ALPHA_TEST) {
                    canvas.blend(originX + tx + shift, originY + ty, argb);
                }
            }
        }
    }

    private boolean touchesGlyph(Glyph glyph, int tx, int ty, long config, int color) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if ((colorAt(glyph, tx + dx, ty + dy, config, color) >>> 24) > ALPHA_TEST) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int colorAt(Glyph glyph, int tx, int ty, long config, int color) {
        int argb = sample(glyph, tx, ty, color);
        if ((config & BOLD) != 0) {
            for (int i = 1; i <= 2; i++) {
                if (inside(glyph, tx - i, ty) && (argb >>> 24) < 255) {
                    argb = sample(glyph, tx - i, ty, color);
                }
            }
        }
        if ((config & UNDERLINE) != 0 && (ty == CELL || ty == CELL + 1)) {
            argb = color;
        }
        if ((config & STRIKETHROUGH) != 0 && (ty == CELL / 2 - 2 || ty == CELL / 2 - 1)) {
            argb = color;
        }
        return argb;
    }

    private static boolean inside(Glyph glyph, int tx, int ty) {
        return tx >= 0 && ty >= 0 && tx < glyph.width && ty < CELL;
    }

    private static int sample(Glyph glyph, int tx, int ty, int color) {
        if (!inside(glyph, tx, ty)) {
            return 0;
        }
        int texel = glyph.page.getRGB(glyph.u + tx, glyph.v + ty);
        int alpha = (texel >>> 24) * (color >>> 24) / 255;
        int red = (texel >> 16 & 0xFF) * (color >> 16 & 0xFF) / 255;
        int green = (texel >> 8 & 0xFF) * (color >> 8 & 0xFF) / 255;
        int blue = (texel & 0xFF) * (color & 0xFF) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private record Glyph(BufferedImage page, int u, int v, int width) {
    }
}
