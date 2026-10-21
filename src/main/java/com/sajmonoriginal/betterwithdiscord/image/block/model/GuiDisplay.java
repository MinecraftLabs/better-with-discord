package com.sajmonoriginal.betterwithdiscord.image.block.model;

public record GuiDisplay(float tx, float ty, float tz, float rx, float ry, float rz, float sx, float sy, float sz) {
    public static final GuiDisplay IDENTITY = new GuiDisplay(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    public static final GuiDisplay BLOCK = new GuiDisplay(0.0F, 0.0F, 0.0F, 30.0F, 225.0F, 0.0F, 0.625F, 0.625F, 0.625F);
}
