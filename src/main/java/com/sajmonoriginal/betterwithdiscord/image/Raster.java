package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class Raster {
    private static final int ALPHA_TEST = 26;

    private Raster() {
    }

    public static BufferedImage draw(List<Quad> quads, int width, int height) {
        int[] pixels = new int[width * height];
        float[] depth = new float[width * height];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        quads.removeIf(quad -> !quad.visible);
        quads.sort(Comparator.comparingDouble(quad -> quad.depth));
        for (Quad quad : quads) {
            quad.draw(pixels, depth, width, height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    public static int tint(int texel, int tint, BufferedImage mask, int tx, int ty, BufferedImage texture) {
        if (tint == -1) {
            return texel;
        }
        float amount = 1.0F;
        if (mask != null) {
            int mx = tx * mask.getWidth() / texture.getWidth();
            int my = ty * mask.getWidth() / texture.getWidth();
            amount = my < mask.getHeight() ? (mask.getRGB(mx, my) >> 16 & 0xFF) / 255.0F : 0.0F;
        }
        int red = mixChannel(texel >> 16 & 0xFF, tint >> 16 & 0xFF, amount);
        int green = mixChannel(texel >> 8 & 0xFF, tint >> 8 & 0xFF, amount);
        int blue = mixChannel(texel & 0xFF, tint & 0xFF, amount);
        return texel & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int mixChannel(int texel, int tint, float amount) {
        return Math.round(texel * (1.0F + (tint / 255.0F - 1.0F) * amount));
    }

    public static final class Quad {
        final BufferedImage texture;
        final BufferedImage mask;
        public final float[] x = new float[4];
        public final float[] y = new float[4];
        public final float[] z = new float[4];
        public final float[] u = new float[4];
        public final float[] v = new float[4];
        public boolean visible;
        public float depth;
        public float light;
        public int tint;
        public float opacity = 1.0F;

        public Quad(BufferedImage texture, BufferedImage mask) {
            this.texture = texture;
            this.mask = mask;
        }

        void draw(int[] pixels, float[] depthBuffer, int width, int height) {
            triangle(0, 1, 2, pixels, depthBuffer, width, height);
            triangle(0, 2, 3, pixels, depthBuffer, width, height);
        }

        private void triangle(int a, int b, int c, int[] pixels, float[] depthBuffer, int width, int height) {
            float area = edge(x[a], y[a], x[b], y[b], x[c], y[c]);
            if (Math.abs(area) < 1.0E-6F) {
                return;
            }
            int minX = Math.max(0, (int) Math.floor(Math.min(x[a], Math.min(x[b], x[c]))));
            int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(x[a], Math.max(x[b], x[c]))));
            int minY = Math.max(0, (int) Math.floor(Math.min(y[a], Math.min(y[b], y[c]))));
            int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(y[a], Math.max(y[b], y[c]))));
            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    float sx = px + 0.5F;
                    float sy = py + 0.5F;
                    float wa = edge(x[b], y[b], x[c], y[c], sx, sy) / area;
                    float wb = edge(x[c], y[c], x[a], y[a], sx, sy) / area;
                    float wc = 1.0F - wa - wb;
                    if (wa < 0 || wb < 0 || wc < 0) {
                        continue;
                    }
                    int index = py * width + px;
                    float pz = wa * z[a] + wb * z[b] + wc * z[c];
                    if (pz < depthBuffer[index] - 1.0E-4F) {
                        continue;
                    }
                    float pu = wa * u[a] + wb * u[b] + wc * u[c];
                    float pv = wa * v[a] + wb * v[b] + wc * v[c];
                    int tx = Math.max(0, Math.min(texture.getWidth() - 1, (int) Math.floor(pu)));
                    int ty = Math.max(0, Math.min(texture.getHeight() - 1, (int) Math.floor(pv)));
                    int texel = texture.getRGB(tx, ty);
                    if (texel >>> 24 < ALPHA_TEST) {
                        continue;
                    }
                    int alpha = Math.round((texel >>> 24) * opacity);
                    int tinted = tint(texel, tint, mask, tx, ty, texture);
                    int r = Math.min(255, Math.round((tinted >> 16 & 0xFF) * light));
                    int g = Math.min(255, Math.round((tinted >> 8 & 0xFF) * light));
                    int bl = Math.min(255, Math.round((tinted & 0xFF) * light));
                    pixels[index] = blend(pixels[index], alpha << 24 | r << 16 | g << 8 | bl);
                    if (alpha == 255) {
                        depthBuffer[index] = pz;
                    }
                }
            }
        }

        private static float edge(float ax, float ay, float bx, float by, float px, float py) {
            return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        }

        private static int blend(int dst, int src) {
            int srcAlpha = src >>> 24;
            if (srcAlpha == 255) {
                return src;
            }
            int dstAlpha = dst >>> 24;
            int outAlpha = srcAlpha + dstAlpha * (255 - srcAlpha) / 255;
            if (outAlpha == 0) {
                return 0;
            }
            int r = ((src >> 16 & 0xFF) * srcAlpha + (dst >> 16 & 0xFF) * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
            int g = ((src >> 8 & 0xFF) * srcAlpha + (dst >> 8 & 0xFF) * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
            int b = ((src & 0xFF) * srcAlpha + (dst & 0xFF) * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
            return outAlpha << 24 | r << 16 | g << 8 | b;
        }
    }
}
