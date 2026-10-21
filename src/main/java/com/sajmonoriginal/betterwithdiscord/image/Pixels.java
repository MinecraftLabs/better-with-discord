package com.sajmonoriginal.betterwithdiscord.image;

final class Pixels {
    static final int ALPHA_TEST = 26;

    private Pixels() {
    }

    static int blend(int dst, int src) {
        int srcAlpha = src >>> 24;
        if (srcAlpha == 255) {
            return src;
        }
        int dstAlpha = dst >>> 24;
        int outAlpha = srcAlpha + dstAlpha * (255 - srcAlpha) / 255;
        if (outAlpha == 0) {
            return 0;
        }
        int red = mix(src >> 16 & 0xFF, srcAlpha, dst >> 16 & 0xFF, dstAlpha, outAlpha);
        int green = mix(src >> 8 & 0xFF, srcAlpha, dst >> 8 & 0xFF, dstAlpha, outAlpha);
        int blue = mix(src & 0xFF, srcAlpha, dst & 0xFF, dstAlpha, outAlpha);
        return outAlpha << 24 | red << 16 | green << 8 | blue;
    }

    static int multiply(int argb, int color) {
        int alpha = (argb >>> 24) * (color >>> 24) / 255;
        int red = (argb >> 16 & 0xFF) * (color >> 16 & 0xFF) / 255;
        int green = (argb >> 8 & 0xFF) * (color >> 8 & 0xFF) / 255;
        int blue = (argb & 0xFF) * (color & 0xFF) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int mix(int src, int srcAlpha, int dst, int dstAlpha, int outAlpha) {
        return (src * srcAlpha + dst * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
    }
}
