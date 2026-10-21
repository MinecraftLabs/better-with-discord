package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.image.BufferedImage;

final class MapRenderer {
    private static final int MARGIN = 7;
    private static final int SIZE = MapSnapshot.SIZE + MARGIN * 2;
    private static final String BACKGROUND = "assets/minecraft/textures/misc/mapbg.png";
    private static final String ICONS = "assets/minecraft/textures/misc/mapicons.png";
    private static final String FLAG = "assets/minecraft/textures/misc/flagicon.png";
    private static final String FLAG_COLORS = "assets/minecraft/textures/misc/colors_flag.png";
    private static final int WAYPOINT_ICON = 5;

    private final ClientAssets assets;

    MapRenderer(ClientAssets assets) {
        this.assets = assets;
    }

    BufferedImage render(MapSnapshot map) {
        Canvas canvas = new Canvas(SIZE, SIZE);
        BufferedImage background = assets.image(BACKGROUND);
        if (background != null) {
            canvas.drawTexture(background, 0, 0, background.getWidth(), background.getHeight(), 0, 0, SIZE, SIZE);
        }
        BufferedImage pixels = new BufferedImage(MapSnapshot.SIZE, MapSnapshot.SIZE, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, MapSnapshot.SIZE, MapSnapshot.SIZE, map.pixels(), 0, MapSnapshot.SIZE);
        canvas.drawTexture(pixels, 0, 0, MapSnapshot.SIZE, MapSnapshot.SIZE, MARGIN, MARGIN, MARGIN + MapSnapshot.SIZE, MARGIN + MapSnapshot.SIZE);
        BufferedImage icons = assets.image(ICONS);
        if (icons != null) {
            for (MapSnapshot.Waypoint waypoint : map.waypoints()) {
                icon(canvas, icons, WAYPOINT_ICON, MARGIN + waypoint.x() / 2.0 + 64, MARGIN + waypoint.z() / 2.0 + 64, Math.PI);
            }
            for (MapSnapshot.Marker marker : map.decorations()) {
                icon(canvas, icons, marker.type(), MARGIN + marker.x() / 2.0 + 64, MARGIN + marker.y() / 2.0 + 64,
                    marker.rotation() * Math.PI * 2 / 16.0);
            }
        }
        BufferedImage flag = assets.image(FLAG);
        BufferedImage colors = assets.image(FLAG_COLORS);
        if (flag != null && colors != null) {
            for (MapSnapshot.Waypoint waypoint : map.waypoints()) {
                flag(canvas, flag, colors, waypoint);
            }
        }
        return canvas.toImage();
    }

    private static void icon(Canvas canvas, BufferedImage icons, int type, double centerX, double centerY, double angle) {
        double cell = icons.getWidth() / 4.0;
        double u0 = type % 4 * cell;
        double v0 = type / 4 * cell;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int scale = Canvas.SCALE;
        int left = (int) Math.floor((centerX - 6) * scale);
        int top = (int) Math.floor((centerY - 6) * scale);
        for (int py = top; py < top + 12 * scale; py++) {
            for (int px = left; px < left + 12 * scale; px++) {
                double dx = (px + 0.5) / scale - centerX;
                double dy = (py + 0.5) / scale - centerY;
                double localX = (dx * cos + dy * sin) / 4.0 + 0.125;
                double localY = (-dx * sin + dy * cos) / 4.0 - 0.125;
                if (localX < -1 || localX >= 1 || localY <= -1 || localY > 1) {
                    continue;
                }
                int tx = (int) (u0 + (localX + 1) / 2.0 * cell);
                int ty = (int) (v0 + (1 - localY) / 2.0 * cell);
                canvas.blend(px, py, icons.getRGB(Math.min(icons.getWidth() - 1, tx), Math.min(icons.getHeight() - 1, ty)));
            }
        }
    }

    private static void flag(Canvas canvas, BufferedImage flag, BufferedImage colors, MapSnapshot.Waypoint waypoint) {
        double cell = flag.getWidth() / 4.0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int colorIndex = 15 - waypoint.colors()[x * 3 + y];
                int color = colors.getRGB(Math.max(0, Math.min(colors.getWidth() - 1, colorIndex)), 0);
                int index = x + y;
                double u0 = index % 4 * cell;
                double v0 = index / 4 * cell;
                double left = MARGIN + x + waypoint.x() / 2.0 + 64 - 0.5;
                double top = MARGIN + y + waypoint.z() / 2.0 + 64 - 2.5;
                BufferedImage tinted = tint(flag, (int) u0, (int) v0, (int) cell, color);
                canvas.drawTexture(tinted, cell, 0, 0, cell, left, top, left + 1, top + 1);
            }
        }
    }

    private static BufferedImage tint(BufferedImage source, int u, int v, int size, int color) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int texel = source.getRGB(Math.min(source.getWidth() - 1, u + x), Math.min(source.getHeight() - 1, v + y));
                int red = (texel >> 16 & 0xFF) * (color >> 16 & 0xFF) / 255;
                int green = (texel >> 8 & 0xFF) * (color >> 8 & 0xFF) / 255;
                int blue = (texel & 0xFF) * (color & 0xFF) / 255;
                result.setRGB(x, y, texel & 0xFF000000 | red << 16 | green << 8 | blue);
            }
        }
        return result;
    }
}
