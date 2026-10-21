package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

public final class Canvas {
    public static final int SCALE = 2;

    private final int width;
    private final int height;
    private final int[] pixels;

    public Canvas(int guiWidth, int guiHeight) {
        this.width = guiWidth * SCALE;
        this.height = guiHeight * SCALE;
        this.pixels = new int[width * height];
    }

    public void blend(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height || argb >>> 24 == 0) {
            return;
        }
        int index = y * width + x;
        pixels[index] = Pixels.blend(pixels[index], argb);
    }

    public void fill(int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0 * SCALE; y < y1 * SCALE; y++) {
            for (int x = x0 * SCALE; x < x1 * SCALE; x++) {
                blend(x, y, argb);
            }
        }
    }

    public void drawTexture(BufferedImage texture, double u0, double v0, double u1, double v1, double x0, double y0, double x1, double y1) {
        int px0 = (int) Math.round(x0 * SCALE);
        int px1 = (int) Math.round(x1 * SCALE);
        int py0 = (int) Math.round(y0 * SCALE);
        int py1 = (int) Math.round(y1 * SCALE);
        if (px1 <= px0 || py1 <= py0) {
            return;
        }
        for (int py = py0; py < py1; py++) {
            double v = v0 + (py + 0.5 - py0) / (py1 - py0) * (v1 - v0);
            int ty = clamp((int) Math.floor(v), texture.getHeight());
            for (int px = px0; px < px1; px++) {
                double u = u0 + (px + 0.5 - px0) / (px1 - px0) * (u1 - u0);
                blend(px, py, texture.getRGB(clamp((int) Math.floor(u), texture.getWidth()), ty));
            }
        }
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    public BufferedImage toImage() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    public static BufferedImage upscale(BufferedImage image, int factor) {
        BufferedImage scaled = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        return new AffineTransformOp(AffineTransform.getScaleInstance(factor, factor), AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(image, scaled);
    }
}
