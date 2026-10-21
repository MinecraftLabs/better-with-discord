package com.sajmonoriginal.betterwithdiscord.image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TooltipRenderer {
    private static final int DEFAULT_PADDING = 3;

    private final BtaFont font;
    private final GuiSprite inner;
    private final GuiSprite border;
    private final int paddingTop;
    private final int paddingBottom;
    private final int paddingLeft;
    private final int paddingRight;

    public TooltipRenderer(ClientAssets assets, BtaFont font, String style) {
        this.font = font;
        GuiSprite styleInner = GuiSprite.load(assets, "tooltip/" + style + "/inner");
        String used = styleInner == null ? "default" : style;
        this.inner = styleInner == null ? GuiSprite.load(assets, "tooltip/default/inner") : styleInner;
        this.border = GuiSprite.load(assets, "tooltip/" + used + "/border");
        int[] padding = padding(assets, used);
        this.paddingTop = padding[0];
        this.paddingBottom = padding[1];
        this.paddingLeft = padding[2];
        this.paddingRight = padding[3];
    }

    private static int[] padding(ClientAssets assets, String style) {
        int[] padding = {DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING};
        try {
            byte[] data = assets.read("assets/minecraft/textures/gui/sprites/tooltip/" + style + "/properties.json");
            if (data == null) {
                return padding;
            }
            JsonElement value = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject().get("padding");
            if (value == null) {
                return padding;
            }
            if (value.isJsonPrimitive()) {
                int all = value.getAsInt();
                return new int[]{all, all, all, all};
            }
            JsonObject sides = value.getAsJsonObject();
            String[] keys = {"top", "bottom", "left", "right"};
            for (int i = 0; i < keys.length; i++) {
                if (sides.has(keys[i])) {
                    padding[i] = sides.get(keys[i]).getAsInt();
                }
            }
            return padding;
        } catch (IOException | RuntimeException e) {
            return padding;
        }
    }

    public BufferedImage render(String tooltip) {
        List<String> lines = BtaFont.lines(tooltip);
        int width = Math.max(0, (int) Math.ceil(font.measure(lines)) - 1);
        int height = Math.max(0, lines.size() * BtaFont.LINE_HEIGHT - 1);
        int textX = 2 + paddingLeft;
        int textY = 2 + paddingTop;
        Canvas canvas = new Canvas(width + 4 + paddingLeft + paddingRight, height + 4 + paddingTop + paddingBottom);
        if (inner != null) {
            inner.draw(canvas, textX - 2, textY - 2, width + 4, height + 4);
        }
        if (border != null) {
            border.draw(canvas, 0, 0, width + 4 + paddingLeft + paddingRight, height + 4 + paddingTop + paddingBottom);
        }
        font.drawLines(canvas, lines, textX, textY, 0xFFFFFFFF, false);
        return canvas.toImage();
    }
}
