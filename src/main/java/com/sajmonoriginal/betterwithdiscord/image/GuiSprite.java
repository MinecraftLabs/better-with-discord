package com.sajmonoriginal.betterwithdiscord.image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public record GuiSprite(BufferedImage texture, String type, int width, int height, int left, int top, int right, int bottom, boolean stretchInner) {

    public static GuiSprite load(ClientAssets assets, String name) {
        String path = "assets/minecraft/textures/gui/sprites/" + name + ".png";
        BufferedImage texture = assets.image(path);
        if (texture == null) {
            return null;
        }
        JsonObject scaling = null;
        try {
            byte[] meta = assets.read(path + ".mcmeta");
            if (meta != null) {
                JsonObject gui = JsonParser.parseString(new String(meta, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("gui");
                scaling = gui == null ? null : gui.getAsJsonObject("scaling");
            }
        } catch (IOException | RuntimeException e) {
            scaling = null;
        }
        if (scaling == null) {
            return new GuiSprite(texture, "stretch", texture.getWidth(), texture.getHeight(), 0, 0, 0, 0, false);
        }

        String type = scaling.has("type") ? scaling.get("type").getAsString() : "stretch";
        int width = scaling.has("width") ? scaling.get("width").getAsInt() : texture.getWidth();
        int height = scaling.has("height") ? scaling.get("height").getAsInt() : texture.getHeight();
        boolean stretchInner = scaling.has("stretch_inner") && scaling.get("stretch_inner").getAsBoolean();
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        JsonElement border = scaling.get("border");
        if (border != null && border.isJsonPrimitive()) {
            left = top = right = bottom = border.getAsInt();
        } else if (border != null) {
            JsonObject sides = border.getAsJsonObject();
            left = sides.get("left").getAsInt();
            top = sides.get("top").getAsInt();
            right = sides.get("right").getAsInt();
            bottom = sides.get("bottom").getAsInt();
        }
        return new GuiSprite(texture, type, width, height, left, top, right, bottom, stretchInner);
    }

    public void draw(Canvas canvas, double x, double y, double w, double h) {
        double uScale = (double) texture.getWidth() / width;
        double vScale = (double) texture.getHeight() / height;
        switch (type) {
            case "tile" -> tile(canvas, 0, 0, width, height, uScale, vScale, x, y, w, h);
            case "nine_slice" -> nineSlice(canvas, uScale, vScale, x, y, w, h);
            default -> canvas.drawTexture(texture, 0, 0, texture.getWidth(), texture.getHeight(), x, y, x + w, y + h);
        }
    }

    private void nineSlice(Canvas canvas, double uScale, double vScale, double x, double y, double w, double h) {
        double[] columns = {0, left, width - right, width};
        double[] rows = {0, top, height - bottom, height};
        double[] targetColumns = {x, x + left, x + w - right, x + w};
        double[] targetRows = {y, y + top, y + h - bottom, y + h};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double pieceX = columns[column];
                double pieceY = rows[row];
                double pieceW = columns[column + 1] - pieceX;
                double pieceH = rows[row + 1] - pieceY;
                double targetX = targetColumns[column];
                double targetY = targetRows[row];
                double targetW = targetColumns[column + 1] - targetX;
                double targetH = targetRows[row + 1] - targetY;
                if (row == 1 && column == 1 && stretchInner) {
                    canvas.drawTexture(texture, pieceX * uScale, pieceY * vScale, (pieceX + pieceW) * uScale, (pieceY + pieceH) * vScale,
                            targetX, targetY, targetX + targetW, targetY + targetH);
                } else {
                    tile(canvas, pieceX, pieceY, pieceW, pieceH, uScale, vScale, targetX, targetY, targetW, targetH);
                }
            }
        }
    }

    private void tile(Canvas canvas, double pieceX, double pieceY, double pieceW, double pieceH, double uScale, double vScale,
                      double x, double y, double w, double h) {
        if (pieceW <= 0 || pieceH <= 0) {
            return;
        }
        for (double offsetY = 0; offsetY < h; offsetY += pieceH) {
            double segmentH = Math.min(pieceH, h - offsetY);
            for (double offsetX = 0; offsetX < w; offsetX += pieceW) {
                double segmentW = Math.min(pieceW, w - offsetX);
                canvas.drawTexture(texture,
                        pieceX * uScale, pieceY * vScale, (pieceX + segmentW) * uScale, (pieceY + segmentH) * vScale,
                        x + offsetX, y + offsetY, x + offsetX + segmentW, y + offsetY + segmentH);
            }
        }
    }
}
