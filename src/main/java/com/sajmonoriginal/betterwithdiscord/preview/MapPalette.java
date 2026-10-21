package com.sajmonoriginal.betterwithdiscord.preview;

import com.sajmonoriginal.betterwithdiscord.image.MapSnapshot;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class MapPalette {
    private static final int SIZE = MapSnapshot.SIZE;
    private static final int COLOR_COUNT = 64;
    private static final int SHADES = 3;
    private static final int LOOKUP_BITS = 5;
    private static final int LOOKUP_SIZE = 1 << LOOKUP_BITS;

    private final int[] colors;
    private final byte[] indices;
    private final byte[] lookup = new byte[LOOKUP_SIZE * LOOKUP_SIZE * LOOKUP_SIZE];

    private MapPalette(int[] colors, byte[] indices) {
        this.colors = colors;
        this.indices = indices;
        int step = 256 / LOOKUP_SIZE;
        for (int r = 0; r < LOOKUP_SIZE; r++) {
            for (int g = 0; g < LOOKUP_SIZE; g++) {
                for (int b = 0; b < LOOKUP_SIZE; b++) {
                    lookup[(r * LOOKUP_SIZE + g) * LOOKUP_SIZE + b] = (byte) nearestSlow(r * step + step / 2, g * step + step / 2, b * step + step / 2);
                }
            }
        }
    }

    static MapPalette create() {
        Map<Integer, Byte> unique = new LinkedHashMap<>();
        for (int colorIndex = 1; colorIndex < COLOR_COUNT; colorIndex++) {
            for (int shade = 0; shade < SHADES; shade++) {
                int index = colorIndex * 4 + shade;
                unique.putIfAbsent(MapSnapshot.color(index) & 0xFFFFFF, (byte) index);
            }
        }
        int[] colors = new int[unique.size()];
        byte[] indices = new byte[unique.size()];
        int i = 0;
        for (Map.Entry<Integer, Byte> entry : unique.entrySet()) {
            colors[i] = entry.getKey();
            indices[i] = entry.getValue();
            i++;
        }
        return new MapPalette(colors, indices);
    }

    byte[] dither(BufferedImage image) {
        byte[] result = new byte[SIZE * SIZE];
        float[] current = new float[(SIZE + 2) * 3];
        float[] next = new float[(SIZE + 2) * 3];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                int base = (x + 1) * 3;
                int red = clamp((argb >> 16 & 0xFF) + current[base]);
                int green = clamp((argb >> 8 & 0xFF) + current[base + 1]);
                int blue = clamp((argb & 0xFF) + current[base + 2]);
                int nearest = nearest(red, green, blue);
                result[x * SIZE + y] = indices[nearest];
                int color = colors[nearest];
                spread(current, next, x, red - (color >> 16 & 0xFF), 0);
                spread(current, next, x, green - (color >> 8 & 0xFF), 1);
                spread(current, next, x, blue - (color & 0xFF), 2);
            }
            float[] swap = current;
            current = next;
            next = swap;
            Arrays.fill(next, 0.0F);
        }
        return result;
    }

    private static void spread(float[] current, float[] next, int x, int error, int channel) {
        int at = (x + 1) * 3 + channel;
        current[at + 3] += error * 7 / 16.0F;
        next[at - 3] += error * 3 / 16.0F;
        next[at] += error * 5 / 16.0F;
        next[at + 3] += error / 16.0F;
    }

    private int nearest(int red, int green, int blue) {
        int shift = 8 - LOOKUP_BITS;
        return lookup[((red >> shift) * LOOKUP_SIZE + (green >> shift)) * LOOKUP_SIZE + (blue >> shift)] & 0xFF;
    }

    private int nearestSlow(int red, int green, int blue) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < colors.length; i++) {
            int dr = red - (colors[i] >> 16 & 0xFF);
            int dg = green - (colors[i] >> 8 & 0xFF);
            int db = blue - (colors[i] & 0xFF);
            int meanRed = (red + (colors[i] >> 16 & 0xFF)) / 2;
            long distance = (long) (512 + meanRed) * dr * dr / 256 + 4L * dg * dg + (long) (767 - meanRed) * db * db / 256;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
